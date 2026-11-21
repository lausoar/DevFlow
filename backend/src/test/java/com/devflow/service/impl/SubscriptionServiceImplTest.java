package com.devflow.service.impl;

import com.devflow.entity.Subscription;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.SubscriptionMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 被移出项目后，订阅列表不能再泄露原项目的标题 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionMapper subscriptionMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private BugMapper bugMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private ProjectAccessService projectAccessService;

    @InjectMocks private SubscriptionServiceImpl service;

    private Subscription subscription(String projectId, String targetId) {
        Subscription subscription = new Subscription();
        subscription.setUserId("u1");
        subscription.setProjectId(projectId);
        subscription.setTargetType("TASK");
        subscription.setTargetId(targetId);
        return subscription;
    }

    @Test
    void listMineOmitsSubscriptionsToProjectsNoLongerVisible() {
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(
                subscription("p-visible", "t-visible"),
                subscription("p-removed", "t-removed")));
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p-visible"));
        Task visibleTask = new Task();
        visibleTask.setId("t-visible");
        visibleTask.setTitle("可见任务");
        when(taskMapper.selectById("t-visible")).thenReturn(visibleTask);

        var rows = service.listMine("u1", null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("projectId")).isEqualTo("p-visible");
        assertThat(rows.get(0).get("title")).isEqualTo("可见任务");
    }

    @Test
    void listMineKeepsGlobalDocumentSubscriptions() {
        Subscription global = subscription(null, "doc-global");
        global.setTargetType("DOCUMENT");
        when(subscriptionMapper.selectList(any())).thenReturn(List.of(global));
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of());

        var rows = service.listMine("u1", "DOCUMENT");

        // 全局文档没有 projectId，不应因项目可见性过滤而被误删
        assertThat(rows).hasSize(1);
    }
}
