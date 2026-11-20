package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Task;
import com.devflow.entity.TaskStatusEvent;
import com.devflow.mapper.TaskDependencyMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.TaskStatusEventMapper;
import com.devflow.service.NotificationRecipients;
import com.devflow.service.NotificationService;
import com.devflow.service.SubscriptionService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStatusEventServiceImplTest {

    @Mock private TaskMapper taskMapper;
    @Mock private NotificationService notificationService;
    @Mock private WorkflowService workflowService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private NotificationRecipients notificationRecipients;
    @Mock private TaskDependencyMapper taskDependencyMapper;
    @Mock private TaskStatusEventMapper taskStatusEventMapper;

    @InjectMocks private TaskServiceImpl service;

    @Test
    void statusChangeAppendsTransitionEvent() {
        Task task = task("TODO");
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO")).thenReturn("TODO");
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE")).thenReturn("DONE");
        when(notificationRecipients.resolve(anyString(), anyString(), anyString(), anyList())).thenReturn(List.of());

        service.updateStatus("t1", "DONE", "operator");

        ArgumentCaptor<TaskStatusEvent> captor = ArgumentCaptor.forClass(TaskStatusEvent.class);
        verify(taskStatusEventMapper).insert(captor.capture());
        TaskStatusEvent event = captor.getValue();
        assertThat(event.getTaskId()).isEqualTo("t1");
        assertThat(event.getProjectId()).isEqualTo("p1");
        assertThat(event.getFromStatus()).isEqualTo("TODO");
        assertThat(event.getToStatus()).isEqualTo("DONE");
        assertThat(event.getFromCategory()).isEqualTo("TODO");
        assertThat(event.getToCategory()).isEqualTo("DONE");
        assertThat(event.getActorId()).isEqualTo("operator");
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void unchangedStatusDoesNotAppendEvent() {
        Task task = task("DOING");
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(1);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DOING"))
                .thenReturn("IN_PROGRESS");
        when(notificationRecipients.resolve(anyString(), anyString(), anyString(), anyList())).thenReturn(List.of());

        service.updateStatus("t1", "DOING", "operator");

        verify(taskStatusEventMapper, never()).insert(any(TaskStatusEvent.class));
    }

    @Test
    void optimisticLockConflictDoesNotAppendEvent() {
        Task task = task("TODO");
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(taskMapper.updateById(task)).thenReturn(0);
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "TODO")).thenReturn("TODO");
        when(workflowService.categoryOf("p1", WorkflowService.TYPE_TASK, "DONE")).thenReturn("DONE");

        assertThatThrownBy(() -> service.updateStatus("t1", "DONE", "operator"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
        verify(taskStatusEventMapper, never()).insert(any(TaskStatusEvent.class));
    }

    private static Task task(String status) {
        Task task = new Task();
        task.setId("t1");
        task.setProjectId("p1");
        task.setReporterId("reporter");
        task.setStatus(status);
        return task;
    }
}
