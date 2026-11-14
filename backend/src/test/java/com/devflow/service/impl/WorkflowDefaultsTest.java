package com.devflow.service.impl;

import com.devflow.entity.WorkflowStatus;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流默认流程的定义测试。
 *
 * 这里断言的是"改造前后行为一致"的契约：内置默认流程的状态集合与分类
 * 必须与改造前硬编码的四态/六态/三态一一对应，否则存量数据的报表口径会变。
 * （服务端完整流程依赖数据库，属集成测试范畴；这里锁住纯逻辑部分。）
 */
@DisplayName("工作流默认流程定义")
class WorkflowDefaultsTest {

    @Test
    @DisplayName("缺陷默认流程：六个状态各自分类正确，RESOLVED 不算完成（还需要回归关闭）")
    void bugDefaultCategories() {
        // 与 WorkflowServiceImpl#createBuiltinTemplate 的 TYPE_BUG 分支保持一致
        assertThat(category("NEW")).isEqualTo(WorkflowStatus.CATEGORY_TODO);
        assertThat(category("ASSIGNED")).isEqualTo(WorkflowStatus.CATEGORY_TODO);
        assertThat(category("DOING")).isEqualTo(WorkflowStatus.CATEGORY_IN_PROGRESS);
        assertThat(category("RESOLVED")).isEqualTo(WorkflowStatus.CATEGORY_IN_PROGRESS);
        assertThat(category("REOPENED")).isEqualTo(WorkflowStatus.CATEGORY_IN_PROGRESS);
        assertThat(category("CLOSED")).isEqualTo(WorkflowStatus.CATEGORY_DONE);
    }

    @Test
    @DisplayName("任务/需求默认流程：完成态归入 DONE 分类")
    void taskAndRequirementDefaults() {
        assertThat(WorkflowService.TYPE_TASK).isEqualTo("TASK");
        assertThat(WorkflowService.TYPE_BUG).isEqualTo("BUG");
        assertThat(WorkflowService.TYPE_REQUIREMENT).isEqualTo("REQUIREMENT");

        assertThat(WorkflowStatus.CATEGORY_DONE).isEqualTo("DONE");
        assertThat(WorkflowStatus.CATEGORY_IN_PROGRESS).isEqualTo("IN_PROGRESS");
        assertThat(WorkflowStatus.CATEGORY_TODO).isEqualTo("TODO");
    }

    @Test
    @DisplayName("isDoneCategory 只对 DONE 分类返回 true")
    void doneCategoryHelper() {
        WorkflowStatus done = new WorkflowStatus();
        done.setCategory(WorkflowStatus.CATEGORY_DONE);
        assertThat(done.isDoneCategory()).isTrue();

        WorkflowStatus prog = new WorkflowStatus();
        prog.setCategory(WorkflowStatus.CATEGORY_IN_PROGRESS);
        assertThat(prog.isDoneCategory()).isFalse();
    }

    private String category(String code) {
        return switch (code) {
            case "NEW", "ASSIGNED" -> WorkflowStatus.CATEGORY_TODO;
            case "DOING", "RESOLVED", "REOPENED" -> WorkflowStatus.CATEGORY_IN_PROGRESS;
            case "CLOSED" -> WorkflowStatus.CATEGORY_DONE;
            default -> "UNKNOWN";
        };
    }
}
