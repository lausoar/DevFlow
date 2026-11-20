package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignAssetVersion;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.RequirementTask;
import com.devflow.entity.Task;
import com.devflow.entity.User;
import com.devflow.mapper.DesignAssetMapper;
import com.devflow.mapper.DesignAssetVersionMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.RequirementTaskMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.DesignAssetService;
import com.devflow.service.ImPushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignAssetServiceImplTest {

    @Mock private DesignAssetMapper designAssetMapper;
    @Mock private DesignAssetVersionMapper versionMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private RequirementTaskMapper requirementTaskMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private AccessGuard guard;
    @Mock private AutomationRuleService automationRuleService;
    @Mock private ImPushService imPushService;

    @InjectMocks private DesignAssetServiceImpl service;

    private DesignAsset asset;

    @BeforeEach
    void setUp() {
        asset = new DesignAsset();
        asset.setId("a1");
        asset.setProjectId("p1");
        asset.setRequirementId("r1");
        asset.setTitle("登录页设计稿");
        asset.setStatus(DesignAsset.STATUS_IN_REVIEW);
        asset.setIsPrimary(1);
        asset.setCurrentVersion(2);
        asset.setCreatedBy("designer");
    }

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setProjectId("p1");
        requirement.setTitle("登录改造");
        requirement.setRequesterId("requester");
        requirement.setDesignStatus("IN_REVIEW");
        return requirement;
    }

    @Test
    void approvedOnPrimaryFiresDesignApprovedWithContext() {
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(designAssetMapper.transitionStatus("a1", DesignAsset.STATUS_IN_REVIEW, DesignAsset.STATUS_APPROVED))
                .thenReturn(1);
        DesignAsset approved = new DesignAsset();
        approved.setId("a1");
        approved.setProjectId("p1");
        approved.setRequirementId("r1");
        approved.setTitle("登录页设计稿");
        approved.setStatus(DesignAsset.STATUS_APPROVED);
        approved.setIsPrimary(1);
        approved.setCurrentVersion(2);
        when(designAssetMapper.selectById("a1")).thenReturn(asset, approved);
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        // resolveRequirementDesignStatus 查询主稿
        when(designAssetMapper.selectList(any())).thenReturn(List.of(approved));
        RequirementTask link = new RequirementTask();
        link.setRequirementId("r1");
        link.setTaskId("t1");
        when(requirementTaskMapper.selectList(any())).thenReturn(List.of(link));
        Task task = new Task();
        task.setId("t1");
        task.setTitle("实现登录页");
        when(taskMapper.selectBatchIds(any())).thenReturn(List.of(task));
        User owner = new User();
        owner.setUsername("product");
        owner.setRealName("产品经理");
        when(userMapper.selectById("requester")).thenReturn(owner);
        when(projectMapper.selectById("p1")).thenReturn(null);

        DesignAsset result = service.changeStatus("a1", DesignAsset.STATUS_APPROVED, "reviewer");

        assertThat(result.getStatus()).isEqualTo(DesignAsset.STATUS_APPROVED);
        ArgumentCaptor<com.devflow.service.AutomationMessage> captor =
                ArgumentCaptor.forClass(com.devflow.service.AutomationMessage.class);
        verify(automationRuleService).fire(eq(DesignAssetService.TRIGGER_DESIGN_APPROVED), eq("p1"),
                eq("a1"), eq("登录页设计稿"), eq("reviewer"), captor.capture());
        String content = captor.getValue().content();
        assertThat(content).contains("登录改造", "v2", "产品经理", "实现登录页");
        assertThat(captor.getValue().actionUrl()).contains("requirement=r1");
    }

    @Test
    void losingConcurrentTransitionReturns409AndNeverNotifies() {
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(designAssetMapper.transitionStatus(anyString(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus("a1", DesignAsset.STATUS_APPROVED, "reviewer"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));

        verify(automationRuleService, never()).fire(any(), any(), any(), any(), any(), any());
    }

    @Test
    void approvedOnNonPrimaryUsesDesignChangedInsteadOfApproved() {
        asset.setIsPrimary(0);
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(designAssetMapper.transitionStatus("a1", DesignAsset.STATUS_IN_REVIEW, DesignAsset.STATUS_APPROVED))
                .thenReturn(1);
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(designAssetMapper.selectList(any())).thenReturn(List.of(asset));
        when(requirementTaskMapper.selectList(any())).thenReturn(List.of());

        service.changeStatus("a1", DesignAsset.STATUS_APPROVED, "reviewer");

        verify(automationRuleService, never()).fire(eq(DesignAssetService.TRIGGER_DESIGN_APPROVED),
                any(), any(), any(), any(), any());
        // 副稿通过仍然通知，但走 DESIGN_CHANGED：不能让它冒充需求"设计已通过"
        verify(automationRuleService).fire(eq(DesignAssetService.TRIGGER_DESIGN_CHANGED),
                any(), any(), any(), any(), any());
    }

    @Test
    void changesRequestedFiresDesignChanged() {
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(designAssetMapper.transitionStatus("a1", DesignAsset.STATUS_IN_REVIEW,
                DesignAsset.STATUS_CHANGES_REQUESTED)).thenReturn(1);
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(designAssetMapper.selectList(any())).thenReturn(List.of(asset));
        when(requirementTaskMapper.selectList(any())).thenReturn(List.of());

        service.changeStatus("a1", DesignAsset.STATUS_CHANGES_REQUESTED, "reviewer");

        verify(automationRuleService).fire(eq(DesignAssetService.TRIGGER_DESIGN_CHANGED),
                any(), any(), any(), any(), any());
    }

    @Test
    void addVersionFiresChangedOncePerCreatedVersion() {
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(designAssetMapper.selectList(any())).thenReturn(List.of(asset));
        when(requirementTaskMapper.selectList(any())).thenReturn(List.of());

        DesignAssetVersion version = new DesignAssetVersion();
        version.setSnapshotUrl("https://figma.example/v3");
        service.addVersion("a1", version, "designer");

        assertThat(version.getVersion()).isEqualTo(3);
        verify(versionMapper).insert(version);
        verify(automationRuleService).fire(eq(DesignAssetService.TRIGGER_DESIGN_CHANGED),
                eq("p1"), eq("a1"), eq("登录页设计稿"), eq("designer"), any());
    }

    @Test
    void duplicateVersionBecomesConflictWithoutSecondNotification() {
        when(designAssetMapper.selectById("a1")).thenReturn(asset);
        when(versionMapper.insert(any(DesignAssetVersion.class)))
                .thenThrow(new DuplicateKeyException("uk_asset_version"));

        assertThatThrownBy(() -> service.addVersion("a1", new DesignAssetVersion(), "designer"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));

        verify(automationRuleService, never()).fire(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolveRequirementDesignStatusPrefersPrimaryAsset() {
        DesignAsset secondary = new DesignAsset();
        secondary.setStatus(DesignAsset.STATUS_IN_REVIEW);
        DesignAsset primary = new DesignAsset();
        primary.setStatus(DesignAsset.STATUS_APPROVED);
        when(designAssetMapper.selectList(any())).thenReturn(List.of(primary, secondary));

        assertThat(service.resolveRequirementDesignStatus("r1")).isEqualTo(DesignAsset.STATUS_APPROVED);
    }

    @Test
    void resolveRequirementDesignStatusReturnsNoneWithoutAssets() {
        when(designAssetMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.resolveRequirementDesignStatus("r1")).isEqualTo("NONE");
        assertThat(service.resolveRequirementDesignStatus(null)).isEqualTo("NONE");
    }
}
