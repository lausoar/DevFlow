package com.devflow.service.impl;

import com.devflow.entity.Task;
import com.devflow.mapper.TaskDependencyMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.TaskStatusEventMapper;
import com.devflow.service.NotificationRecipients;
import com.devflow.service.NotificationService;
import com.devflow.service.SubscriptionService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskServiceImpl 的状态变更语义。
 *
 * 覆盖第 9 批修的两处：
 *   1. completed_at 只在进出 DONE 分类时变化 —— 之前燃尽图拿 updated_at 当完成时间，
 *      改标题就重画历史曲线；
 *   2. 乐观锁：带过期版本号时必须拒绝，而不是静默覆盖别人的修改。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskServiceImpl 状态变更与乐观锁")
class TaskServiceImplTest {

    @Mock private TaskMapper taskMapper;
    @Mock private NotificationService notificationService;
    @Mock private WorkflowService workflowService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private NotificationRecipients notificationRecipients;
    @Mock private TaskDependencyMapper taskDependencyMapper;
    @Mock private TaskStatusEventMapper taskStatusEventMapper;
    @Mock private com.devflow.service.ProjectAccessService projectAccessService;

    @InjectMocks private TaskServiceImpl service;

    private Task task;

    @BeforeEach
    void setUp() {
        // argThat 断言会真实解析 LambdaWrapper，需要 TableInfo 缓存（CI 全新 JVM 也必须可用）
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(Task.class) == null) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                    Task.class);
        }
        task = new Task();
        task.setId("t1");
        task.setProjectId("p1");
        task.setTitle("示例任务");
        task.setStatus("TODO");
        task.setVersion(1);
        task.setReporterId("u-reporter");
    }

    @Test
    @DisplayName("进入 DONE 分类时写入 completed_at")
    void enteringDoneSetsCompletedAt() {
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO"))
                .thenReturn("TODO");
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn("DONE");
        when(workflowService.labelOf(anyString(), anyString(), anyString())).thenReturn("完成");
        when(notificationRecipients.resolve(any(), any(), any(), any())).thenReturn(List.of());

        service.updateStatus("t1", "DONE", "u-operator");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
        verify(taskStatusEventMapper).insert(any(com.devflow.entity.TaskStatusEvent.class));
    }

    @Test
    @DisplayName("离开 DONE 分类时清空 completed_at")
    void leavingDoneClearsCompletedAt() {
        task.setStatus("DONE");
        task.setCompletedAt(LocalDateTime.now().minusDays(3));
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn("DONE");
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO"))
                .thenReturn("TODO");
        when(workflowService.labelOf(anyString(), anyString(), anyString())).thenReturn("待办");
        when(notificationRecipients.resolve(any(), any(), any(), any())).thenReturn(List.of());

        service.updateStatus("t1", "TODO", "u-operator");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("重复进入 DONE 不刷新已有完成时间（历史曲线要稳定）")
    void reEnteringDoneKeepsOriginalCompletionTime() {
        LocalDateTime original = LocalDateTime.now().minusDays(5);
        task.setStatus("DONE");
        task.setCompletedAt(original);
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn("DONE");
        when(workflowService.labelOf(anyString(), anyString(), anyString())).thenReturn("完成");
        when(notificationRecipients.resolve(any(), any(), any(), any())).thenReturn(List.of());

        service.updateStatus("t1", "DONE", "u-operator");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isEqualTo(original);
    }

    @Test
    @DisplayName("非 DONE 状态不写 completed_at")
    void nonDoneStatusLeavesCompletedAtNull() {
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO"))
                .thenReturn("TODO");
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DOING"))
                .thenReturn("IN_PROGRESS");
        when(workflowService.labelOf(anyString(), anyString(), anyString())).thenReturn("进行中");
        when(notificationRecipients.resolve(any(), any(), any(), any())).thenReturn(List.of());

        service.updateStatus("t1", "DOING", "u-operator");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("工作流查不到分类时不抛异常（回落为字面 DONE 判断）")
    void workflowFailureFallsBackGracefully() {
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO"))
                .thenReturn("TODO");
        // statusCategory 需要先拿到正常分类；只有 isDoneStatus 的后续查询失败，
        // 才能继续验证它原有的字面 DONE 回退。
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn("DONE")
                .thenThrow(new RuntimeException("工作流配置损坏"));
        when(workflowService.labelOf(anyString(), anyString(), anyString())).thenReturn("完成");
        when(notificationRecipients.resolve(any(), any(), any(), any())).thenReturn(List.of());

        service.updateStatus("t1", "DONE", "u-operator");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("乐观锁：过期版本号被拒绝，不写库")
    void staleVersionIsRejected() {
        task.setVersion(3);
        when(taskMapper.selectById("t1")).thenReturn(task);

        Task patch = new Task();
        patch.setId("t1");
        patch.setTitle("并发修改");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.update("t1", patch, 1))
                .isInstanceOf(com.devflow.common.exception.BusinessException.class)
                .hasMessageContaining("已被他人修改");

        verify(taskMapper, never()).updateById(any(Task.class));
    }

    @Test
    @DisplayName("乐观锁：不带版本号时保持旧行为（不加版本条件）")
    void nullVersionSkipsTheCheck() {
        task.setVersion(3);
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);

        Task patch = new Task();
        patch.setId("t1");
        patch.setTitle("普通修改");

        service.update("t1", patch, null);

        verify(taskMapper).updateById(any(Task.class));
        assertThat(patch.getVersion()).isNull();
    }

    @Test
    @DisplayName("删除任务时清理双向依赖")
    void deleteCleansDependencies() {
        service.delete("t1", "u-operator");

        verify(taskDependencyMapper).delete(any());
        // 改用带审计的软删除后，不再是 deleteById
        verify(taskMapper).softDelete("t1", "u-operator");
    }

    @Test
    void myTasksAreLimitedToCurrentlyVisibleProjects() {
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p1"));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        service.listMyTasks("u1");

        verify(taskMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("assignee_id") && sql.contains("project_id") && sql.contains("IN");
        }));
    }

    @Test
    void myTasksAreEmptyWhenNoProjectIsVisible() {
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of());

        assertThat(service.listMyTasks("u1")).isEmpty();
        verify(taskMapper, org.mockito.Mockito.never()).selectList(any());
    }
}
