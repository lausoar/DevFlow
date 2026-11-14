package com.devflow.service;

import com.devflow.entity.TestCycle;
import com.devflow.entity.TestPlan;

import java.util.List;
import java.util.Map;

/**
 * 测试计划与轮次。
 *
 * 有了"计划 → 轮次 → 用例"这层组织，执行记录才回答得了
 * "这一轮测了什么、进度多少、通过率如何"，而不只是散落的用例平铺列表。
 */
public interface TestPlanService {

    List<TestPlan> listPlans(String projectId);

    TestPlan getPlan(String planId);

    TestPlan createPlan(String projectId, TestPlan plan, String operatorId);

    TestPlan updatePlan(String planId, TestPlan patch, String operatorId);

    void deletePlan(String planId, String operatorId);

    /** 计划下的轮次（含每个轮次的用例数与执行进度） */
    List<Map<String, Object>> listCycles(String planId);

    TestCycle createCycle(String planId, TestCycle cycle, String operatorId);

    TestCycle updateCycle(String cycleId, TestCycle patch, String operatorId);

    void deleteCycle(String cycleId, String operatorId);

    /** 往轮次里加用例（批量；已存在则跳过） */
    int addCases(String cycleId, List<String> caseIds, String operatorId);

    /** 从轮次移除用例 */
    void removeCase(String cycleId, String caseId, String operatorId);

    /** 轮次详情：用例清单 + 每个用例在本轮的最新执行结果 + 进度统计 */
    Map<String, Object> cycleDetail(String cycleId);
}
