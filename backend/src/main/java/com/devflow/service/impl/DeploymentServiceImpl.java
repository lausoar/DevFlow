package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.DeployEnvironment;
import com.devflow.entity.Deployment;
import com.devflow.entity.ProjectRelease;
import com.devflow.mapper.DeployEnvironmentMapper;
import com.devflow.mapper.DeploymentMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.ProjectReleaseMapper;
import com.devflow.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署与发布闭环实现。
 *
 * 几条产品约定（都是从 DORA 口径反推的）：
 * - "部署"是一个明确动作，落记录；"部署成功"才计入部署频率；
 * - 变更前置时间 = 本版本首个提交时间 → 部署完成时间（有 first_commit_at 才计算）；
 * - 变更失败率 = 失败部署数 / 全部部署数（含回滚导致的失败）；
 * - MTTR 用"失败部署 → 下一个成功部署"的时间差做近似（无事故表时的合理近似）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final DeployEnvironmentMapper envMapper;
    private final DeploymentMapper deploymentMapper;
    private final ProjectReleaseMapper releaseMapper;
    private final ProjectMemberMapper projectMemberMapper;

    // ==================== 环境 ====================

    @Override
    public List<DeployEnvironment> listEnvironments(String projectId) {
        return envMapper.selectList(new LambdaQueryWrapper<DeployEnvironment>()
                .eq(DeployEnvironment::getProjectId, projectId)
                .orderByAsc(DeployEnvironment::getSort)
                .orderByAsc(DeployEnvironment::getCreatedAt));
    }

    @Override
    public DeployEnvironment createEnvironment(String projectId, DeployEnvironment env, String operatorId) {
        if (!StringUtils.hasText(env.getName())) {
            throw new BusinessException(400, "环境名称不能为空");
        }
        Long exists = envMapper.selectCount(new LambdaQueryWrapper<DeployEnvironment>()
                .eq(DeployEnvironment::getProjectId, projectId)
                .eq(DeployEnvironment::getName, env.getName().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "该项目已有同名环境");
        }
        env.setId(null);
        env.setProjectId(projectId);
        env.setName(env.getName().trim());
        if (!StringUtils.hasText(env.getType())) {
            env.setType("TEST");
        }
        if (env.getRequiresApproval() == null) {
            // 生产环境默认需要审批：这是变更管理的基本要求
            env.setRequiresApproval("PROD".equals(env.getType()) ? 1 : 0);
        }
        if (env.getSort() == null) {
            env.setSort(0);
        }
        env.setDeleted(0);
        envMapper.insert(env);
        return env;
    }

    @Override
    public DeployEnvironment updateEnvironment(String envId, DeployEnvironment patch, String operatorId) {
        DeployEnvironment existing = envMapper.selectById(envId);
        if (existing == null) {
            throw new BusinessException(404, "环境不存在");
        }
        patch.setId(envId);
        patch.setProjectId(existing.getProjectId());
        envMapper.updateById(patch);
        return envMapper.selectById(envId);
    }

    @Override
    public void deleteEnvironment(String envId, String operatorId) {
        if (envMapper.selectById(envId) == null) {
            throw new BusinessException(404, "环境不存在");
        }
        Long deployments = deploymentMapper.selectCount(new LambdaQueryWrapper<Deployment>()
                .eq(Deployment::getEnvironmentId, envId));
        if (deployments != null && deployments > 0) {
            // 有部署历史的环境不物理删除：历史记录是审计证据
            throw new BusinessException(409, "该环境已有部署记录，不能删除（可改名或归档）");
        }
        envMapper.deleteById(envId);
    }

    // ==================== 部署记录 ====================

    @Override
    public List<Deployment> listDeployments(String projectId, String environmentId,
                                            String releaseId, int limit) {
        LambdaQueryWrapper<Deployment> wrapper = new LambdaQueryWrapper<Deployment>()
                .eq(Deployment::getProjectId, projectId)
                .orderByDesc(Deployment::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200));
        if (StringUtils.hasText(environmentId)) {
            wrapper.eq(Deployment::getEnvironmentId, environmentId);
        }
        if (StringUtils.hasText(releaseId)) {
            wrapper.eq(Deployment::getReleaseId, releaseId);
        }
        return deploymentMapper.selectList(wrapper);
    }

    @Override
    public Deployment getDeployment(String deploymentId) {
        Deployment d = deploymentMapper.selectById(deploymentId);
        if (d == null) {
            throw new BusinessException(404, "部署记录不存在");
        }
        return d;
    }

    @Override
    @Transactional
    public Deployment createDeployment(String projectId, String environmentId,
                                       Deployment request, String operatorId) {
        DeployEnvironment env = envMapper.selectById(environmentId);
        if (env == null || !projectId.equals(env.getProjectId())) {
            throw new BusinessException(404, "环境不存在");
        }

        // 环境互斥：同一环境不允许有未完成的部署
        Long pending = deploymentMapper.selectCount(new LambdaQueryWrapper<Deployment>()
                .eq(Deployment::getEnvironmentId, environmentId)
                .in(Deployment::getStatus, List.of("PENDING_APPROVAL", "APPROVED", "DEPLOYING")));
        if (pending != null && pending > 0) {
            throw new BusinessException(409, "该环境已有进行中的部署，请先完成或取消");
        }

        Deployment d = new Deployment();
        d.setProjectId(projectId);
        d.setEnvironmentId(environmentId);
        d.setTriggeredBy(operatorId);
        d.setNote(request.getNote());
        d.setCommitSha(request.getCommitSha());

        // 版本信息来自 release（快照版本号，避免版本被删后历史失真）
        if (StringUtils.hasText(request.getReleaseId())) {
            ProjectRelease release = releaseMapper.selectById(request.getReleaseId());
            if (release == null || !projectId.equals(release.getProjectId())) {
                throw new BusinessException(404, "版本不存在");
            }
            d.setReleaseId(release.getId());
            d.setVersion(release.getVersion());
        } else if (StringUtils.hasText(request.getVersion())) {
            d.setVersion(request.getVersion());
        }
        d.setFirstCommitAt(request.getFirstCommitAt());

        d.setStatus(Integer.valueOf(1).equals(env.getRequiresApproval())
                ? "PENDING_APPROVAL" : "APPROVED");
        deploymentMapper.insert(d);
        return d;
    }

    @Override
    public Deployment approve(String deploymentId, String operatorId, String note) {
        Deployment d = getDeployment(deploymentId);
        if (!"PENDING_APPROVAL".equals(d.getStatus())) {
            throw new BusinessException(400, "只有待审批的部署可以审批");
        }
        if (operatorId.equals(d.getTriggeredBy())) {
            throw new BusinessException(403, "不能审批自己发起的部署");
        }
        Deployment patch = new Deployment();
        patch.setId(deploymentId);
        patch.setStatus("APPROVED");
        patch.setApprovedBy(operatorId);
        patch.setApprovedAt(LocalDateTime.now());
        patch.setApprovalNote(note);
        deploymentMapper.updateById(patch);
        return getDeployment(deploymentId);
    }

    @Override
    public Deployment reject(String deploymentId, String operatorId, String note) {
        Deployment d = getDeployment(deploymentId);
        if (!"PENDING_APPROVAL".equals(d.getStatus())) {
            throw new BusinessException(400, "只有待审批的部署可以驳回");
        }
        if (operatorId.equals(d.getTriggeredBy())) {
            throw new BusinessException(403, "不能驳回自己发起的部署");
        }
        Deployment patch = new Deployment();
        patch.setId(deploymentId);
        patch.setStatus("CANCELLED");
        patch.setApprovedBy(operatorId);
        patch.setApprovedAt(LocalDateTime.now());
        patch.setApprovalNote(note);
        deploymentMapper.updateById(patch);
        return getDeployment(deploymentId);
    }

    @Override
    public Deployment markDeploying(String deploymentId, String operatorId) {
        Deployment d = getDeployment(deploymentId);
        if (!"APPROVED".equals(d.getStatus())) {
            throw new BusinessException(400, "只有已批准的部署可以开始部署");
        }
        Deployment patch = new Deployment();
        patch.setId(deploymentId);
        patch.setStatus("DEPLOYING");
        patch.setStartedAt(LocalDateTime.now());
        deploymentMapper.updateById(patch);
        return getDeployment(deploymentId);
    }

    @Override
    public Deployment markFinished(String deploymentId, boolean success, String note, String operatorId) {
        Deployment d = getDeployment(deploymentId);
        if (!"DEPLOYING".equals(d.getStatus()) && !"APPROVED".equals(d.getStatus())) {
            throw new BusinessException(400, "当前状态不可结束部署");
        }
        Deployment patch = new Deployment();
        patch.setId(deploymentId);
        patch.setStatus(success ? "SUCCEEDED" : "FAILED");
        patch.setFinishedAt(LocalDateTime.now());
        if (StringUtils.hasText(note)) {
            patch.setNote(note);
        }
        if (patch.getStartedAt() == null && "DEPLOYING".equals(d.getStatus())) {
            patch.setStartedAt(d.getStartedAt());
        }
        deploymentMapper.updateById(patch);
        return getDeployment(deploymentId);
    }

    @Override
    @Transactional
    public Deployment rollback(String deploymentId, String operatorId, String note) {
        Deployment target = getDeployment(deploymentId);
        if (!"SUCCEEDED".equals(target.getStatus())) {
            throw new BusinessException(400, "只能回滚成功的部署");
        }
        DeployEnvironment env = envMapper.selectById(target.getEnvironmentId());
        if (env == null) {
            throw new BusinessException(404, "环境不存在");
        }
        if (Integer.valueOf(1).equals(env.getRequiresApproval())) {
            throw new BusinessException(400, "受保护环境的回滚需要走一次新的部署审批");
        }

        Deployment rb = new Deployment();
        rb.setProjectId(target.getProjectId());
        rb.setEnvironmentId(target.getEnvironmentId());
        rb.setReleaseId(target.getReleaseId());
        rb.setVersion(target.getVersion());
        rb.setCommitSha(target.getCommitSha());
        rb.setFirstCommitAt(target.getFirstCommitAt());
        rb.setTriggeredBy(operatorId);
        rb.setRollbackOf(target.getId());
        rb.setStatus("DEPLOYING");
        rb.setStartedAt(LocalDateTime.now());
        rb.setNote(StringUtils.hasText(note) ? note : "回滚到 " + target.getVersion());
        deploymentMapper.insert(rb);

        // 被回滚的那次部署标记为回滚（不计入成功部署，计入变更失败）
        Deployment patch = new Deployment();
        patch.setId(target.getId());
        patch.setStatus("ROLLED_BACK");
        deploymentMapper.updateById(patch);
        return rb;
    }

    // ==================== DORA ====================

    @Override
    public Map<String, Object> doraMetrics(String projectId, int days) {
        int window = Math.min(Math.max(days, 1), 365);
        LocalDateTime since = LocalDateTime.now().minusDays(window);

        // 生产环境（type=PROD）的部署才计入 DORA
        List<DeployEnvironment> prodEnvs = envMapper.selectList(new LambdaQueryWrapper<DeployEnvironment>()
                .eq(DeployEnvironment::getProjectId, projectId)
                .eq(DeployEnvironment::getType, "PROD"));
        if (prodEnvs.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("hasProdEnvironment", false);
            empty.put("windowDays", window);
            empty.put("deploymentFrequency", 0);
            empty.put("deploymentFrequencyLabel", "—");
            empty.put("leadTimeHours", null);
            empty.put("changeFailureRate", null);
            empty.put("mttrHours", null);
            empty.put("totalDeployments", 0);
            empty.put("failedDeployments", 0);
            empty.put("sampleSize", 0);
            empty.put("note", "尚未配置生产环境（环境类型设为 PROD），DORA 指标无从统计");
            return empty;
        }
        List<String> envIds = prodEnvs.stream().map(DeployEnvironment::getId).toList();
        List<Deployment> all = deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                .eq(Deployment::getProjectId, projectId)
                .in(Deployment::getEnvironmentId, envIds)
                .ge(Deployment::getCreatedAt, since)
                .orderByAsc(Deployment::getFinishedAt));

        List<Deployment> finished = all.stream()
                .filter(d -> d.getFinishedAt() != null)
                .filter(d -> List.of("SUCCEEDED", "FAILED", "ROLLED_BACK").contains(d.getStatus()))
                .toList();
        List<Deployment> succeeded = finished.stream()
                .filter(d -> "SUCCEEDED".equals(d.getStatus()))
                .toList();
        List<Deployment> failed = finished.stream()
                .filter(d -> !"SUCCEEDED".equals(d.getStatus()))
                .toList();

        // 1) 部署频率：成功部署次数 / 天数 → 折算成"每天/每周"的可读标签
        double perDay = window > 0 ? (double) succeeded.size() / window : 0;
        String freqLabel;
        if (perDay >= 1) {
            freqLabel = String.format("%.1f 次/天", perDay);
        } else if (perDay > 0) {
            freqLabel = String.format("%.1f 次/周", perDay * 7);
        } else {
            freqLabel = "0 次";
        }

        // 2) 变更前置时间：first_commit_at → finished_at 的中位数
        List<Double> leadTimes = succeeded.stream()
                .filter(d -> d.getFirstCommitAt() != null && d.getFinishedAt() != null)
                .map(d -> Duration.between(d.getFirstCommitAt(), d.getFinishedAt()).toMinutes() / 60.0)
                .filter(h -> h >= 0)
                .sorted()
                .toList();
        Double leadTimeHours = leadTimes.isEmpty() ? null : median(leadTimes);

        // 3) 变更失败率：失败（含被回滚）/ 全部
        Double changeFailureRate = finished.isEmpty() ? null
                : Math.round(failed.size() * 1000.0 / finished.size()) / 10.0;

        // 4) 恢复时间（MTTR 近似）：每次失败到其后首次成功的时间差，取中位数
        List<Double> recovery = new ArrayList<>();
        List<Deployment> sorted = new ArrayList<>(finished);
        sorted.sort(Comparator.comparing(Deployment::getFinishedAt));
        for (int i = 0; i < sorted.size(); i++) {
            Deployment d = sorted.get(i);
            if ("SUCCEEDED".equals(d.getStatus())) {
                continue;
            }
            for (int j = i + 1; j < sorted.size(); j++) {
                if ("SUCCEEDED".equals(sorted.get(j).getStatus())) {
                    recovery.add(Duration.between(d.getFinishedAt(),
                            sorted.get(j).getFinishedAt()).toMinutes() / 60.0);
                    break;
                }
            }
        }
        Double mttrHours = recovery.isEmpty() ? null : median(recovery.stream().sorted().toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasProdEnvironment", true);
        m.put("windowDays", window);
        m.put("deploymentFrequency", Math.round(perDay * 100) / 100.0);
        m.put("deploymentFrequencyLabel", freqLabel);
        m.put("leadTimeHours", leadTimeHours == null ? null : Math.round(leadTimeHours * 10) / 10.0);
        m.put("changeFailureRate", changeFailureRate);
        m.put("mttrHours", mttrHours == null ? null : Math.round(mttrHours * 10) / 10.0);
        m.put("totalDeployments", finished.size());
        m.put("failedDeployments", failed.size());
        m.put("sampleSize", succeeded.size());
        m.put("leadTimeSamples", leadTimes.size());
        if (finished.size() < 5) {
            m.put("note", "样本量不足（少于 5 次部署），指标仅供参考");
        }
        return m;
    }

    /** 中位数（入参需已排序） */
    private double median(List<Double> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
