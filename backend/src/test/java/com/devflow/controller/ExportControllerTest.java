package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务端导出：可见性收口、公式注入中和、审计写入。
 */
@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    @Mock private TaskMapper taskMapper;
    @Mock private BugMapper bugMapper;
    @Mock private com.devflow.mapper.SprintMapper sprintMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private AuditService auditService;
    @Mock private AccessGuard guard;
    @Mock private org.springframework.security.core.Authentication authentication;

    @InjectMocks private ExportController controller;

    @org.junit.jupiter.api.BeforeEach
    void registerTableInfo() {
        for (Class<?> entity : List.of(Task.class, Bug.class)) {
            if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entity) == null) {
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                        new org.apache.ibatis.builder.MapperBuilderAssistant(
                                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                        entity);
            }
        }
    }

    private Project project() {
        Project project = new Project();
        project.setId("p1");
        project.setName("平台");
        project.setEnterpriseId("e1");
        return project;
    }

    @Test
    void exportRefusesProjectTheCallerCannotSee() {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectMapper.selectById("p1")).thenReturn(project());
        when(projectAccessService.canView("u1", "p1")).thenReturn(false);

        assertThatThrownBy(() -> controller.exportTasks("p1", null, null, null, null, authentication, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(404));
        verify(taskMapper, never()).selectList(any());
        verify(auditService, never()).logRequired(any(), any(), any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void exportWritesAuditRowWithRowCount() throws Exception {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectMapper.selectById("p1")).thenReturn(project());
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        when(taskMapper.selectList(any())).thenReturn(List.of(task("t1"), task("t2")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportTasks("p1", null, null, null, null, authentication, response);

        assertThat(response.getContentType()).contains("text/csv");
        assertThat(response.getContentAsString()).contains("标题", "t1", "t2");
        verify(auditService).logRequired(eq("u1"), eq("e1"), eq("p1"), eq("export.tasks"),
                eq("project"), eq("p1"), eq("导出任务 CSV：2 行"));
    }

    @Test
    void exportNeutralizesFormulaInjection() throws Exception {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectMapper.selectById("p1")).thenReturn(project());
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        Task dangerous = task("t1");
        dangerous.setTitle("=cmd|' /C calc'!A0");
        when(taskMapper.selectList(any())).thenReturn(List.of(dangerous));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportTasks("p1", null, null, null, null, authentication, response);

        assertThat(response.getContentAsString()).contains("'=cmd");
    }

    @Test
    void bugExportExcludesPendingGuestSubmissions() throws Exception {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectMapper.selectById("p1")).thenReturn(project());
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        Bug formal = new Bug();
        formal.setId("b1");
        formal.setProjectId("p1");
        formal.setTitle("正式缺陷");
        formal.setStatus("NEW");
        formal.setReporterType("USER");
        formal.setReporterId("u2");
        when(bugMapper.selectList(any())).thenReturn(List.of(formal));
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportBugs("p1", null, null, null, null, null, authentication, response);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Bug>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(bugMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("pending");
        assertThat(response.getContentAsString()).contains("正式缺陷");
    }

    private Task task(String id) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId("p1");
        task.setTitle(id);
        task.setStatus("DOING");
        return task;
    }
}
