package com.devflow.controller;

import com.devflow.dto.response.EnterpriseStatsResponse;
import com.devflow.entity.Enterprise;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 企业统计导出：成员范围收口 + 指标与审计一致 */
@ExtendWith(MockitoExtension.class)
class StatisticsExportControllerTest {

    @Mock private StatisticsService statisticsService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private UserMapper userMapper;
    @Mock private AccessGuard guard;
    @Mock private AuditService auditService;
    @Mock private Authentication authentication;

    @InjectMocks private StatisticsController controller;

    @Test
    void enterpriseExportUsesCurrentStatsAndWritesAudit() throws Exception {
        when(guard.userId(authentication)).thenReturn("u1");
        when(guard.requireEnterpriseMember("u1", "e1")).thenReturn(new Enterprise());
        EnterpriseStatsResponse stats = EnterpriseStatsResponse.builder()
                .projectOverview(EnterpriseStatsResponse.ProjectOverview.builder()
                        .totalProjects(4).activeProjects(3).totalMembers(12).build())
                .taskOverview(EnterpriseStatsResponse.TaskOverview.builder()
                        .totalTasks(100).completedTasks(60).overdueTasks(5).completionRate(60).build())
                .bugOverview(EnterpriseStatsResponse.BugOverview.builder()
                        .totalBugs(20).openBugs(8).resolutionRate(60).build())
                .requirementOverview(EnterpriseStatsResponse.RequirementOverview.builder()
                        .totalRequirements(30).openRequirements(10).completedRequirements(20).build())
                .build();
        when(statisticsService.getEnterpriseStats("e1")).thenReturn(stats);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportEnterpriseSummary("e1", authentication, response);

        assertThat(response.getContentType()).contains("text/csv");
        String csv = response.getContentAsString();
        assertThat(csv).contains("项目总数,4", "任务总数,100", "Bug 总数,20");
        verify(auditService).logRequired(eq("u1"), eq("e1"), eq(null), eq("export.enterprise"),
                eq("enterprise"), eq("e1"), eq("导出企业统计 CSV：13 行"));
    }
}
