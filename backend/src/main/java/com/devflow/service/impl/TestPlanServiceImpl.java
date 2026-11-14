package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.TestCase;
import com.devflow.entity.TestCycle;
import com.devflow.entity.TestCycleCase;
import com.devflow.entity.TestExecution;
import com.devflow.entity.TestPlan;
import com.devflow.mapper.TestCaseMapper;
import com.devflow.mapper.TestCycleCaseMapper;
import com.devflow.mapper.TestCycleMapper;
import com.devflow.mapper.TestExecutionMapper;
import com.devflow.mapper.TestPlanMapper;
import com.devflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 测试计划与轮次实现。
 *
 * 统计口径：轮次进度 = 本轮已执行用例数 / 本轮用例总数；
 * 通过率按"每个用例在本轮的最新一次执行结果"计算，避免重复执行把通过率刷高。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl implements TestPlanService {

    private final TestPlanMapper planMapper;
    private final TestCycleMapper cycleMapper;
    private final TestCycleCaseMapper cycleCaseMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestExecutionMapper executionMapper;

    // ==================== 计划 ====================

    @Override
    public List<TestPlan> listPlans(String projectId) {
        return planMapper.selectList(new LambdaQueryWrapper<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .orderByDesc(TestPlan::getCreatedAt));
    }

    @Override
    public TestPlan getPlan(String planId) {
        TestPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(404, "测试计划不存在");
        }
        return plan;
    }

    @Override
    public TestPlan createPlan(String projectId, TestPlan plan, String operatorId) {
        if (!StringUtils.hasText(plan.getName())) {
            throw new BusinessException(400, "计划名称不能为空");
        }
        plan.setId(null);
        plan.setProjectId(projectId);
        plan.setOwnerId(StringUtils.hasText(plan.getOwnerId()) ? plan.getOwnerId() : operatorId);
        if (!StringUtils.hasText(plan.getStatus())) {
            plan.setStatus(TestPlan.STATUS_PLANNING);
        }
        plan.setDeleted(0);
        planMapper.insert(plan);
        return plan;
    }

    @Override
    public TestPlan updatePlan(String planId, TestPlan patch, String operatorId) {
        TestPlan existing = getPlan(planId);
        patch.setId(planId);
        patch.setProjectId(existing.getProjectId());
        planMapper.updateById(patch);
        return getPlan(planId);
    }

    @Override
    public void deletePlan(String planId, String operatorId) {
        getPlan(planId);
        // 轮次随计划一起逻辑删除，避免留下孤儿
        List<TestCycle> cycles = cycleMapper.selectList(
                new LambdaQueryWrapper<TestCycle>().eq(TestCycle::getPlanId, planId));
        for (TestCycle c : cycles) {
            cycleCaseMapper.delete(new LambdaQueryWrapper<TestCycleCase>()
                    .eq(TestCycleCase::getCycleId, c.getId()));
            cycleMapper.deleteById(c.getId());
        }
        planMapper.deleteById(planId);
    }

    // ==================== 轮次 ====================

    @Override
    public List<Map<String, Object>> listCycles(String planId) {
        getPlan(planId);
        List<TestCycle> cycles = cycleMapper.selectList(new LambdaQueryWrapper<TestCycle>()
                .eq(TestCycle::getPlanId, planId)
                .orderByAsc(TestCycle::getCreatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TestCycle c : cycles) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cycle", c);
            row.putAll(progress(c.getId()));
            out.add(row);
        }
        return out;
    }

    @Override
    public TestCycle createCycle(String planId, TestCycle cycle, String operatorId) {
        TestPlan plan = getPlan(planId);
        if (!StringUtils.hasText(cycle.getName())) {
            throw new BusinessException(400, "轮次名称不能为空");
        }
        cycle.setId(null);
        cycle.setPlanId(planId);
        cycle.setProjectId(plan.getProjectId());
        if (!StringUtils.hasText(cycle.getStatus())) {
            cycle.setStatus(TestCycle.STATUS_NOT_STARTED);
        }
        cycleMapper.insert(cycle);
        return cycle;
    }

    @Override
    public TestCycle updateCycle(String cycleId, TestCycle patch, String operatorId) {
        TestCycle existing = getCycle(cycleId);
        patch.setId(cycleId);
        patch.setPlanId(existing.getPlanId());
        patch.setProjectId(existing.getProjectId());
        cycleMapper.updateById(patch);
        return getCycle(cycleId);
    }

    @Override
    public void deleteCycle(String cycleId, String operatorId) {
        getCycle(cycleId);
        cycleCaseMapper.delete(new LambdaQueryWrapper<TestCycleCase>()
                .eq(TestCycleCase::getCycleId, cycleId));
        cycleMapper.deleteById(cycleId);
    }

    @Override
    @Transactional
    public int addCases(String cycleId, List<String> caseIds, String operatorId) {
        TestCycle cycle = getCycle(cycleId);
        if (caseIds == null || caseIds.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (String caseId : caseIds) {
            TestCase testCase = testCaseMapper.selectById(caseId);
            // 只允许加入同项目的用例，避免跨项目串范围
            if (testCase == null || !cycle.getProjectId().equals(testCase.getProjectId())) {
                continue;
            }
            Long exists = cycleCaseMapper.selectCount(new LambdaQueryWrapper<TestCycleCase>()
                    .eq(TestCycleCase::getCycleId, cycleId)
                    .eq(TestCycleCase::getCaseId, caseId));
            if (exists != null && exists > 0) {
                continue;
            }
            TestCycleCase row = new TestCycleCase();
            row.setCycleId(cycleId);
            row.setCaseId(caseId);
            row.setProjectId(cycle.getProjectId());
            cycleCaseMapper.insert(row);
            added++;
        }
        return added;
    }

    @Override
    public void removeCase(String cycleId, String caseId, String operatorId) {
        getCycle(cycleId);
        cycleCaseMapper.delete(new LambdaQueryWrapper<TestCycleCase>()
                .eq(TestCycleCase::getCycleId, cycleId)
                .eq(TestCycleCase::getCaseId, caseId));
    }

    @Override
    public Map<String, Object> cycleDetail(String cycleId) {
        TestCycle cycle = getCycle(cycleId);
        List<TestCycleCase> links = cycleCaseMapper.selectList(
                new LambdaQueryWrapper<TestCycleCase>().eq(TestCycleCase::getCycleId, cycleId));

        // 本轮各用例的最新执行结果（一次查询后在内存取最新）
        List<TestExecution> execs = executionMapper.selectList(
                new LambdaQueryWrapper<TestExecution>().eq(TestExecution::getCycleId, cycleId));
        Map<String, TestExecution> latestByCase = new HashMap<>();
        for (TestExecution e : execs) {
            TestExecution prev = latestByCase.get(e.getCaseId());
            if (prev == null || e.getCreatedAt().isAfter(prev.getCreatedAt())) {
                latestByCase.put(e.getCaseId(), e);
            }
        }

        List<Map<String, Object>> cases = new ArrayList<>();
        for (TestCycleCase link : links) {
            TestCase testCase = testCaseMapper.selectById(link.getCaseId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("link", link);
            row.put("case", testCase);
            TestExecution latest = latestByCase.get(link.getCaseId());
            row.put("latestResult", latest != null ? latest.getResult() : null);
            row.put("latestBugId", latest != null ? latest.getBugId() : null);
            cases.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cycle", cycle);
        result.put("cases", cases);
        result.putAll(progress(cycleId));
        return result;
    }

    // ==================== 内部 ====================

    private TestCycle getCycle(String cycleId) {
        TestCycle cycle = cycleMapper.selectById(cycleId);
        if (cycle == null) {
            throw new BusinessException(404, "测试轮次不存在");
        }
        return cycle;
    }

    /** 轮次进度：总数 / 已执行 / 通过 / 失败 / 阻塞 / 跳过 / 通过率 */
    private Map<String, Object> progress(String cycleId) {
        List<TestCycleCase> links = cycleCaseMapper.selectList(
                new LambdaQueryWrapper<TestCycleCase>().eq(TestCycleCase::getCycleId, cycleId));
        Set<String> caseIds = links.stream().map(TestCycleCase::getCaseId).collect(Collectors.toSet());

        Map<String, TestExecution> latestByCase = new HashMap<>();
        if (!caseIds.isEmpty()) {
            List<TestExecution> execs = executionMapper.selectList(
                    new LambdaQueryWrapper<TestExecution>().eq(TestExecution::getCycleId, cycleId));
            for (TestExecution e : execs) {
                if (!caseIds.contains(e.getCaseId())) {
                    continue;
                }
                TestExecution prev = latestByCase.get(e.getCaseId());
                if (prev == null || e.getCreatedAt().isAfter(prev.getCreatedAt())) {
                    latestByCase.put(e.getCaseId(), e);
                }
            }
        }

        int total = caseIds.size();
        int executed = latestByCase.size();
        long pass = latestByCase.values().stream().filter(e -> "PASS".equals(e.getResult())).count();
        long fail = latestByCase.values().stream().filter(e -> "FAIL".equals(e.getResult())).count();
        long blocked = latestByCase.values().stream().filter(e -> "BLOCKED".equals(e.getResult())).count();
        long skip = latestByCase.values().stream().filter(e -> "SKIP".equals(e.getResult())).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("executed", executed);
        m.put("pass", pass);
        m.put("fail", fail);
        m.put("blocked", blocked);
        m.put("skip", skip);
        m.put("passRate", executed > 0 ? Math.round(pass * 1000.0 / executed) / 10.0 : 0.0);
        m.put("progress", total > 0 ? Math.round(executed * 1000.0 / total) / 10.0 : 0.0);
        return m;
    }
}
