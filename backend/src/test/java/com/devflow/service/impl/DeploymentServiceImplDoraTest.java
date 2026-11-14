package com.devflow.service.impl;

import com.devflow.entity.DeployEnvironment;
import com.devflow.entity.Deployment;
import com.devflow.mapper.DeployEnvironmentMapper;
import com.devflow.mapper.DeploymentMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.ProjectReleaseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DORA 指标计算单测。
 *
 * 这几个口径最容易写错，且错了不会报错、只会给出看似合理的错误数字：
 * - 没有生产环境时不能返回 0，要明确"无从统计"；
 * - 变更前置时间只统计有提交时间的成功部署（缺样本时返回 null 而不是 0 小时）；
 * - 失败率的分母是全部已完成部署（含被回滚的）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentServiceImpl DORA 指标")
class DeploymentServiceImplDoraTest {

    @Mock
    private DeployEnvironmentMapper envMapper;
    @Mock
    private DeploymentMapper deploymentMapper;
    @Mock
    private ProjectReleaseMapper releaseMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;

    @InjectMocks
    private DeploymentServiceImpl service;

    private static final String PROJECT = "p1";

    private static DeployEnvironment prodEnv() {
        DeployEnvironment env = new DeployEnvironment();
        env.setId("env-prod");
        env.setProjectId(PROJECT);
        env.setType("PROD");
        return env;
    }

    private static Deployment dep(String status, LocalDateTime finished,
                                  LocalDateTime firstCommit) {
        Deployment d = new Deployment();
        d.setId("d-" + status + "-" + finished);
        d.setProjectId(PROJECT);
        d.setEnvironmentId("env-prod");
        d.setStatus(status);
        d.setFinishedAt(finished);
        d.setFirstCommitAt(firstCommit);
        d.setCreatedAt(finished.minusDays(1));
        return d;
    }

    @Test
    @DisplayName("未配置生产环境：明确返回无从统计，而不是 0 次部署")
    void noProdEnvironment() {
        when(envMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> m = service.doraMetrics(PROJECT, 30);

        assertThat(m.get("hasProdEnvironment")).isEqualTo(false);
        assertThat(m.get("deploymentFrequencyLabel")).isEqualTo("—");
        assertThat(m.get("leadTimeHours")).isNull();
        assertThat(m.get("note")).asString().contains("生产环境");
    }

    @Test
    @DisplayName("前置时间：成功部署的提交→完成中位数，失败部署不计入")
    void leadTimeMedianOfSuccessfulOnly() {
        LocalDateTime base = LocalDateTime.now().minusDays(5);
        List<Deployment> deps = List.of(
                // 成功：提交 → 完成 2 小时
                dep("SUCCEEDED", base.plusHours(2), base),
                // 成功：4 小时
                dep("SUCCEEDED", base.plusDays(1).plusHours(4), base.plusDays(1)),
                // 成功：6 小时
                dep("SUCCEEDED", base.plusDays(2).plusHours(6), base.plusDays(2)),
                // 失败：不计入前置时间
                dep("FAILED", base.plusDays(3).plusHours(100), base.plusDays(3)));
        when(envMapper.selectList(any())).thenReturn(List.of(prodEnv()));
        when(deploymentMapper.selectList(any())).thenReturn(deps);

        Map<String, Object> m = service.doraMetrics(PROJECT, 30);

        assertThat(m.get("hasProdEnvironment")).isEqualTo(true);
        // 中位数是 4 小时
        assertThat(m.get("leadTimeHours")).isEqualTo(4.0);
        assertThat(m.get("totalDeployments")).isEqualTo(4);
        assertThat(m.get("failedDeployments")).isEqualTo(1);
    }

    @Test
    @DisplayName("变更失败率：被回滚的部署计入失败")
    void changeFailureRateIncludesRolledBack() {
        LocalDateTime base = LocalDateTime.now().minusDays(3);
        List<Deployment> deps = List.of(
                dep("SUCCEEDED", base, base.minusHours(1)),
                dep("FAILED", base.plusDays(1), base),
                dep("ROLLED_BACK", base.plusDays(2), base),
                dep("SUCCEEDED", base.plusDays(3), base));
        when(envMapper.selectList(any())).thenReturn(List.of(prodEnv()));
        when(deploymentMapper.selectList(any())).thenReturn(deps);

        Map<String, Object> m = service.doraMetrics(PROJECT, 30);

        // 2 失败 / 4 总计 = 50%
        assertThat(m.get("changeFailureRate")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("MTTR：失败到其后首次成功的间隔中位数")
    void mttrBetweenFailureAndNextSuccess() {
        LocalDateTime base = LocalDateTime.now().minusDays(3);
        List<Deployment> deps = List.of(
                dep("FAILED", base, base.minusHours(1)),
                dep("SUCCEEDED", base.plusHours(2), base.minusHours(1)),
                dep("FAILED", base.plusDays(1), base),
                dep("SUCCEEDED", base.plusDays(1).plusHours(6), base));
        when(envMapper.selectList(any())).thenReturn(List.of(prodEnv()));
        when(deploymentMapper.selectList(any())).thenReturn(deps);

        Map<String, Object> m = service.doraMetrics(PROJECT, 30);

        // 两次恢复：2 小时与 6 小时 → 中位数 4 小时
        assertThat(m.get("mttrHours")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("样本不足时给出提示，而不是让用户误以为指标可信")
    void smallSampleNote() {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        when(envMapper.selectList(any())).thenReturn(List.of(prodEnv()));
        when(deploymentMapper.selectList(any())).thenReturn(List.of(
                dep("SUCCEEDED", base, base.minusHours(1))));

        Map<String, Object> m = service.doraMetrics(PROJECT, 30);
        assertThat(m.get("note")).asString().contains("样本量不足");
    }
}
