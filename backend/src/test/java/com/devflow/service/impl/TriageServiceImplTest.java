package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.TriageItem;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectFeedbackMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.TriageItemMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import com.devflow.service.FullTextSearchService;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageServiceImplTest {

    @Mock private TriageItemMapper triageItemMapper;
    @Mock private BugMapper bugMapper;
    @Mock private ProjectFeedbackMapper projectFeedbackMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private NotificationService notificationService;
    @Mock private FullTextSearchService fullTextSearchService;
    @Mock private AuditService auditService;

    @InjectMocks private TriageServiceImpl service;

    private TriageItem item;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 需要 TableInfo 缓存；单测没有跑 MyBatis 扫描，手动注册
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(Bug.class) == null) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(
                            new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                    Bug.class);
        }
        item = new TriageItem();
        item.setId("i1");
        item.setProjectId("p1");
        item.setEnterpriseId("e1");
        item.setSource(TriageItem.SOURCE_GUEST_BUG);
        item.setSourceRefId("b1");
        item.setTitle("登录崩溃");
        item.setStatus(TriageItem.STATUS_PENDING);
        item.setSlaDueAt(LocalDateTime.now().minusHours(1));
    }

    private void allowViewAndEdit() {
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        when(permissionService.hasProjectPermission("u1", "p1", com.devflow.common.PermissionCodes.BUG_EDIT))
                .thenReturn(true);
    }

    @Test
    void acceptClaimsPendingBugAndMarksItemAccepted() {
        allowViewAndEdit();
        when(triageItemMapper.selectById("i1")).thenReturn(item);
        Bug bug = new Bug();
        bug.setId("b1");
        bug.setProjectId("p1");
        bug.setPending(1);
        when(bugMapper.selectById("b1")).thenReturn(bug);
        when(bugMapper.update(eq(null), any())).thenReturn(1);
        when(triageItemMapper.decide(eq("i1"), eq(TriageItem.STATUS_ACCEPTED), eq("b1"), eq("u1"), any()))
                .thenReturn(1);
        TriageItem accepted = new TriageItem();
        accepted.setId("i1");
        accepted.setStatus(TriageItem.STATUS_ACCEPTED);
        accepted.setResultRefId("b1");
        accepted.setProjectId("p1");
        accepted.setTitle("登录崩溃");
        when(triageItemMapper.selectById("i1")).thenReturn(item, accepted);
        when(projectMapper.selectById("p1")).thenReturn(new Project());

        var result = service.accept("i1", "u1");

        assertThat(result.get("status")).isEqualTo(TriageItem.STATUS_ACCEPTED);
        assertThat(result.get("resultRefId")).isEqualTo("b1");
        verify(triageItemMapper).decide(eq("i1"), eq(TriageItem.STATUS_ACCEPTED), eq("b1"), eq("u1"), any());
    }

    @Test
    void acceptRefusesWhenBugAlreadyHandledElsewhere() {
        allowViewAndEdit();
        when(triageItemMapper.selectById("i1")).thenReturn(item);
        Bug bug = new Bug();
        bug.setId("b1");
        bug.setProjectId("p1");
        bug.setPending(1);
        when(bugMapper.selectById("b1")).thenReturn(bug);
        when(bugMapper.update(eq(null), any())).thenReturn(0);

        assertThatThrownBy(() -> service.accept("i1", "u1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
        verify(triageItemMapper, never()).decide(any(), any(), any(), any(), any());
    }

    @Test
    void secondDeciderGets409AndCannotOverwriteConclusion() {
        // 状态已不是 PENDING：在任何权限判断之前就应短路成 409
        item.setStatus(TriageItem.STATUS_ACCEPTED);
        when(triageItemMapper.selectById("i1")).thenReturn(item);

        assertThatThrownBy(() -> service.decline("i1", "u1", "duplicate"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    @Test
    void mergeRequiresTargetInSameProject() {
        allowViewAndEdit();
        when(triageItemMapper.selectById("i1")).thenReturn(item);
        Bug target = new Bug();
        target.setId("b2");
        target.setProjectId("p2");
        when(bugMapper.selectById("b2")).thenReturn(target);

        assertThatThrownBy(() -> service.merge("i1", "b2", "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该项目");
    }

    @Test
    void enqueueUsesProjectSlaHoursAndIsIdempotent() {
        Project project = new Project();
        project.setId("p1");
        project.setEnterpriseId("e1");
        project.setTriageSlaHours(8);
        when(projectMapper.selectById("p1")).thenReturn(project);
        when(triageItemMapper.insertIfAbsent(any())).thenReturn(1);

        TriageItem created = service.enqueue("p1", TriageItem.SOURCE_GUEST_BUG, "b1", "崩溃", "游客");

        assertThat(created).isNotNull();
        assertThat(created.getSlaDueAt()).isNotNull();
        assertThat(created.getEnterpriseId()).isEqualTo("e1");
        // 重复入箱：唯一键冲突返回 0，调用方拿到 null（不再重复通知）
        when(triageItemMapper.insertIfAbsent(any())).thenReturn(0);
        assertThat(service.enqueue("p1", TriageItem.SOURCE_GUEST_BUG, "b1", "崩溃", "游客")).isNull();
    }

    @Test
    void slaNotificationIsClaimedAtomically() {
        when(triageItemMapper.markSlaNotified(eq("i1"), any())).thenReturn(0);
        service.notifySlaBreach(item);
        verify(notificationService, never()).createNotification(any(), anyString(), anyString(), anyString(), anyString());

        when(triageItemMapper.markSlaNotified(eq("i1"), any())).thenReturn(1);
        Project project = new Project();
        project.setId("p1");
        project.setOwnerId("owner");
        when(projectMapper.selectById("p1")).thenReturn(project);
        service.notifySlaBreach(item);
        verify(notificationService).createNotification(eq("owner"), eq("TRIAGE"), anyString(), anyString(), anyString());
    }

    @Test
    void similarHintsSkipPendingBugs() {
        when(triageItemMapper.selectById("i1")).thenReturn(item);
        when(fullTextSearchService.searchIds(eq("BUG"), anyString(), eq(List.of("p1")), eq(5)))
                .thenReturn(List.of("b1", "b2"));
        Bug pending = new Bug();
        pending.setId("b1");
        pending.setPending(1);
        Bug formal = new Bug();
        formal.setId("b2");
        formal.setPending(0);
        formal.setTitle("登录崩溃");
        formal.setStatus("NEW");
        when(bugMapper.selectBatchIds(any())).thenReturn(List.of(pending, formal));

        var hints = service.similar("i1");

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0).get("id")).isEqualTo("b2");
    }
}
