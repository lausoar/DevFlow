package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.devflow.entity.AutomationRule;
import com.devflow.entity.Bug;
import com.devflow.entity.User;
import com.devflow.mapper.AutomationRuleMapper;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动化规则单测：重点覆盖 BUG_CREATED 自动指派"必须落库"，
 * 以及"同时配置指派 + 通知"时通知不能被跳过（审计报告 §B1/§B4）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutomationRuleServiceImpl 自动指派与通知")
class AutomationRuleServiceImplTest {

    @Mock
    private AutomationRuleMapper automationRuleMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private AutomationRuleServiceImpl service;

    /**
     * LambdaUpdateWrapper 需要 MyBatis-Plus 的实体内省缓存；
     * Spring 环境由 mapper 扫描自动建立，纯单测里必须手动初始化一次。
     */
    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Bug.class);
    }

    private static final String PROJECT = "p1";
    private static final String REPORTER = "reporter";
    private static final String ASSIGNEE = "assignee";

    private static Bug bug() {
        Bug b = new Bug();
        b.setId("b1");
        b.setProjectId(PROJECT);
        b.setTitle("登录失败");
        b.setStatus("NEW");
        return b;
    }

    private static AutomationRule rule(String assignTo, String notify) {
        AutomationRule r = new AutomationRule();
        r.setId("r1");
        r.setProjectId(PROJECT);
        r.setName("自动分派");
        r.setTriggerType("BUG_CREATED");
        r.setEnabled(1);
        r.setAssignToUserId(assignTo);
        r.setNotifyUserId(notify);
        return r;
    }

    @Test
    @DisplayName("自动指派：命中规则时写库并通知被指派人")
    void autoAssignWritesBackAndNotifies() {
        when(automationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule(ASSIGNEE, null)));
        when(bugMapper.update(isNull(), any())).thenReturn(1);

        Bug b = bug();
        String assigned = service.autoAssignBug(PROJECT, b, REPORTER);

        assertThat(assigned).isEqualTo(ASSIGNEE);
        // 内存对象同步更新（调用方据此返回给前端）
        assertThat(b.getAssigneeId()).isEqualTo(ASSIGNEE);
        assertThat(b.getStatus()).isEqualTo("ASSIGNED");
        // 关键：必须落到数据库，而不是只改内存字段
        verify(bugMapper).update(isNull(), any());
        verify(notificationService).createNotification(
                eq(ASSIGNEE), eq("BUG_UPDATE"), eq("Bug 自动指派"),
                contains("登录失败"), contains("?tab=bugs"));
    }

    @Test
    @DisplayName("自动指派：已被别人抢先指派（update 返回 0）时不覆盖、不通知")
    void autoAssignLosesRace() {
        when(automationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule(ASSIGNEE, null)));
        // Mockito 的 int 默认返回值就是 0，等价于 "条件更新没命中任何行"

        assertThat(service.autoAssignBug(PROJECT, bug(), REPORTER)).isNull();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("自动指派：已指派的 Bug 不动")
    void autoAssignSkipsAssigned() {
        Bug b = bug();
        b.setAssigneeId("someone");
        assertThat(service.autoAssignBug(PROJECT, b, REPORTER)).isNull();
        verify(bugMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("自动指派：规则指向报告人自己时跳过")
    void autoAssignSkipsSelf() {
        when(automationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule(REPORTER, null)));

        assertThat(service.autoAssignBug(PROJECT, bug(), REPORTER)).isNull();
        verify(bugMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("fire：同时配置指派与通知时，通知照常发出（不再被 continue 吞掉）")
    void fireNotifiesEvenWhenRuleAlsoAssigns() {
        when(automationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule(ASSIGNEE, "watcher")));

        int handled = service.fire("BUG_CREATED", PROJECT, "b1", "登录失败", REPORTER);

        verify(notificationService).createNotification(
                eq("watcher"), eq("SYSTEM"), eq("自动化: BUG_CREATED"),
                contains("登录失败"), eq("/projects/" + PROJECT));
        // 指派 + 通知各算一次
        assertThat(handled).isEqualTo(2);
    }

    @Test
    @DisplayName("fire：通知对象就是事件创建人时不发")
    void fireSkipsCreator() {
        when(automationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule(null, REPORTER)));

        assertThat(service.fire("BUG_CREATED", PROJECT, "b1", "登录失败", REPORTER)).isZero();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }
}
