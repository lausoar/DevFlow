package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.TestCase;
import com.devflow.entity.TestExecution;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.TestCaseMapper;
import com.devflow.mapper.TestCaseVersionMapper;
import com.devflow.mapper.TestCycleCaseMapper;
import com.devflow.mapper.TestExecutionMapper;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.BugService;
import com.devflow.service.ImPushService;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseControllerTest {

    @Mock private TestCaseMapper testCaseMapper;
    @Mock private TestExecutionMapper testExecutionMapper;
    @Mock private TestCycleCaseMapper cycleCaseMapper;
    @Mock private TestCaseVersionMapper testCaseVersionMapper;
    @Mock private WorkflowService workflowService;
    @Mock private BugService bugService;
    @Mock private BugMapper bugMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private PermissionService permissionService;
    @Mock private NotificationService notificationService;
    @Mock private ImPushService imPushService;
    @Mock private AutomationRuleService automationRuleService;
    @Mock private Authentication authentication;

    @InjectMocks private TestCaseController controller;

    @BeforeEach
    void authenticate() {
        when(authentication.getPrincipal()).thenReturn("tester");
    }

    private TestCase testCase() {
        TestCase testCase = new TestCase();
        testCase.setId("c1");
        testCase.setProjectId("p1");
        testCase.setCreatorId("tester");
        testCase.setTitle("登录用例");
        testCase.setVersion(1);
        return testCase;
    }

    private TestCase allowCaseManagement() {
        TestCase testCase = testCase();
        when(testCaseMapper.selectById("c1")).thenReturn(testCase);
        when(projectAccessService.canView("tester", "p1")).thenReturn(true);
        when(permissionService.hasProjectPermission("tester", "p1", PermissionCodes.TESTCASE_MANAGE))
                .thenReturn(true);
        return testCase;
    }

    private void allowBugCreation() {
        when(permissionService.hasProjectPermission("tester", "p1", PermissionCodes.BUG_CREATE))
                .thenReturn(true);
    }

    private TestExecution execution(String id, String result, String bugId) {
        TestExecution execution = new TestExecution();
        execution.setId(id);
        execution.setCaseId("c1");
        execution.setProjectId("p1");
        execution.setResult(result);
        execution.setBugId(bugId);
        return execution;
    }

    @Test
    void passDoesNotFireCaseFail() {
        allowCaseManagement();

        controller.execute("c1", Map.of("result", "PASS"), authentication);

        verify(automationRuleService, never()).fire(any(), any(), any(), any(), any());
    }

    @Test
    void failFiresCaseFail() {
        allowCaseManagement();

        controller.execute("c1", Map.of("result", "FAIL"), authentication);

        verify(automationRuleService).fire("CASE_FAIL", "p1", "c1", "登录用例", "tester");
    }

    @Test
    void toBugClaimsLatestFailureWithPreGeneratedIdAndUsesCustomStatus() {
        TestCase testCase = allowCaseManagement();
        testCase.setCreatorId("creator");
        allowBugCreation();
        TestExecution failed = execution("e-fail", "FAIL", null);
        when(testExecutionMapper.selectByCase("c1"))
                .thenReturn(List.of(execution("e-new-pass", "PASS", null), failed));
        when(testExecutionMapper.claimBug(eq("e-fail"), anyString())).thenReturn(1);
        when(workflowService.initialStatus("p1", WorkflowService.TYPE_BUG)).thenReturn("TRIAGE");
        when(bugService.create(eq("p1"), any(Bug.class), eq("tester")))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var response = controller.toBug("c1", Map.of("note", "回归失败"), authentication);

        ArgumentCaptor<String> claimedBugId = ArgumentCaptor.forClass(String.class);
        verify(testExecutionMapper).claimBug(eq("e-fail"), claimedBugId.capture());
        ArgumentCaptor<Bug> created = ArgumentCaptor.forClass(Bug.class);
        verify(bugService).create(eq("p1"), created.capture(), eq("tester"));
        assertThat(created.getValue().getId()).isEqualTo(claimedBugId.getValue()).isNotBlank();
        assertThat(created.getValue().getStatus()).isEqualTo("TRIAGE");
        assertThat(response.getData().getId()).isEqualTo(claimedBugId.getValue());
        verify(notificationService).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    void repeatToBugReturnsAlreadyLinkedBugWithoutSideEffects() {
        allowCaseManagement();
        allowBugCreation();
        TestExecution failed = execution("e-fail", "FAIL", "b-existing");
        when(testExecutionMapper.selectByCase("c1")).thenReturn(List.of(failed));
        Bug linked = new Bug();
        linked.setId("b-existing");
        when(bugService.getById("b-existing")).thenReturn(linked);

        Bug result = controller.toBug("c1", Map.of(), authentication).getData();

        assertThat(result).isSameAs(linked);
        verify(testExecutionMapper, never()).claimBug(any(), any());
        verify(bugService, never()).create(any(), any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    void lostClaimReturnsWinnersBugWithoutDuplicateCreate() {
        allowCaseManagement();
        allowBugCreation();
        TestExecution failed = execution("e-fail", "FAIL", null);
        when(testExecutionMapper.selectByCase("c1")).thenReturn(List.of(failed));
        when(testExecutionMapper.claimBug(eq("e-fail"), anyString())).thenReturn(0);
        when(testExecutionMapper.selectByIdForUpdate("e-fail"))
                .thenReturn(execution("e-fail", "FAIL", "b-winner"));
        Bug winner = new Bug();
        winner.setId("b-winner");
        when(bugMapper.selectByIdForUpdate("b-winner")).thenReturn(winner);

        Bug result = controller.toBug("c1", Map.of(), authentication).getData();

        assertThat(result).isSameAs(winner);
        verify(testExecutionMapper).selectByIdForUpdate("e-fail");
        verify(bugMapper).selectByIdForUpdate("b-winner");
        verify(bugService, never()).getById(any());
        verify(bugService, never()).create(any(), any(), any());
        verify(workflowService, never()).initialStatus(any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    void toBugRejectsCaseWithoutFailedExecution() {
        allowCaseManagement();
        allowBugCreation();
        when(testExecutionMapper.selectByCase("c1"))
                .thenReturn(List.of(execution("e-pass", "PASS", null),
                        execution("e-blocked", "BLOCKED", null)));

        assertThatThrownBy(() -> controller.toBug("c1", Map.of(), authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可转缺陷");

        verify(testExecutionMapper, never()).claimBug(any(), any());
        verify(bugService, never()).create(any(), any(), any());
    }

    @Test
    void creationFailurePropagatesInsideTransactionSoClaimRollsBack() throws Exception {
        allowCaseManagement();
        allowBugCreation();
        when(testExecutionMapper.selectByCase("c1"))
                .thenReturn(List.of(execution("e-fail", "FAIL", null)));
        when(testExecutionMapper.claimBug(eq("e-fail"), anyString())).thenReturn(1);
        when(workflowService.initialStatus("p1", WorkflowService.TYPE_BUG)).thenReturn("TRIAGE");
        when(bugService.create(eq("p1"), any(Bug.class), eq("tester")))
                .thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> controller.toBug("c1", Map.of(), authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insert failed");

        Transactional transactional = TestCaseController.class
                .getMethod("toBug", String.class, Map.class, Authentication.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }
}
