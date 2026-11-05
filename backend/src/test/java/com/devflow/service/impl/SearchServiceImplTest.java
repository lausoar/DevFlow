package com.devflow.service.impl;

import com.devflow.entity.Bug;
import com.devflow.entity.Document;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.DocumentMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 全局搜索服务单测：关键词边界、可见性（走 ProjectAccessService 统一口径）、结果形状。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl 全局搜索（成员口径）")
class SearchServiceImplTest {

    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private SearchServiceImpl service;

    private static final String USER_ID = "u1";

    private Project project() {
        Project p = new Project();
        p.setId("p1");
        p.setName("测试项目");
        p.setDescription("描述");
        return p;
    }

    @Test
    @DisplayName("空关键词：返回五组空结果，且不做任何查询")
    void emptyKeywordReturnsEmptyGroups() {
        Map<String, Object> result = service.search(USER_ID, "   ");

        assertThat(result.get("projects")).asList().isEmpty();
        assertThat(result.get("tasks")).asList().isEmpty();
        assertThat(result.get("bugs")).asList().isEmpty();
        assertThat(result.get("requirements")).asList().isEmpty();
        assertThat(result.get("documents")).asList().isEmpty();
        verifyNoInteractions(projectAccessService, projectMapper, taskMapper, bugMapper, requirementMapper, documentMapper);
    }

    @Test
    @DisplayName("null 关键词：等同空关键词")
    void nullKeywordHandled() {
        Map<String, Object> result = service.search(USER_ID, null);
        assertThat(result.get("bugs")).asList().isEmpty();
        verifyNoInteractions(projectAccessService, projectMapper);
    }

    @Test
    @DisplayName("可见项目集为空：五组全空，不触碰业务表（非成员/无企业越权防护）")
    void noVisibleProjectsMeansNoResults() {
        when(projectAccessService.visibleProjectIds(USER_ID)).thenReturn(List.of());

        Map<String, Object> result = service.search(USER_ID, "导出");

        assertThat(result.get("projects")).asList().isEmpty();
        assertThat(result.get("tasks")).asList().isEmpty();
        assertThat(result.get("bugs")).asList().isEmpty();
        assertThat(result.get("requirements")).asList().isEmpty();
        assertThat(result.get("documents")).asList().isEmpty();
        verifyNoInteractions(projectMapper, taskMapper, bugMapper, requirementMapper, documentMapper);
    }

    @Test
    @DisplayName("命中结果：统一条目形状 id/title/projectId/subtitle/createdAt")
    void resultsAreShapedUniformly() {
        when(projectAccessService.visibleProjectIds(USER_ID)).thenReturn(List.of("p1"));
        // projectMapper 查两次：先按可见清单取项目，再按名称/描述搜索
        when(projectMapper.selectList(any())).thenReturn(List.of(project()));

        Task task = new Task();
        task.setId("t1");
        task.setTitle("编写导出功能");
        task.setProjectId("p1");
        task.setStatus("DOING");
        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        Bug bug = new Bug();
        bug.setId("b1");
        bug.setTitle("导出乱码");
        bug.setProjectId("p1");
        bug.setStatus("NEW");
        when(bugMapper.selectList(any())).thenReturn(List.of(bug));

        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setTitle("支持导出");
        requirement.setProjectId("p1");
        requirement.setStatus("APPROVED");
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));

        Document document = new Document();
        document.setId("d1");
        document.setTitle("导出说明");
        document.setProjectId("p1");
        when(documentMapper.selectList(any())).thenReturn(List.of(document));

        Map<String, Object> result = service.search(USER_ID, "导出");

        assertThat(result.get("projects")).asList().hasSize(1);
        assertThat(result.get("tasks")).asList().hasSize(1);
        assertThat(result.get("bugs")).asList().hasSize(1);
        assertThat(result.get("requirements")).asList().hasSize(1);
        assertThat(result.get("documents")).asList().hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> taskItem = (Map<String, Object>) ((List<?>) result.get("tasks")).get(0);
        assertThat(taskItem.get("id")).isEqualTo("t1");
        assertThat(taskItem.get("title")).isEqualTo("编写导出功能");
        assertThat(taskItem.get("projectId")).isEqualTo("p1");
        assertThat(taskItem.get("subtitle")).isEqualTo("DOING");
    }

    @Test
    @DisplayName("某类无命中：只缺该组，其他组不受影响")
    void partialHitsKeepOtherGroups() {
        when(projectAccessService.visibleProjectIds(USER_ID)).thenReturn(List.of("p1"));
        // 项目名称/描述搜索无命中
        when(projectMapper.selectList(any())).thenReturn(List.of());

        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(bugMapper.selectList(any())).thenReturn(List.of());

        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setTitle("关键词命中");
        requirement.setProjectId("p1");
        requirement.setStatus("APPROVED");
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement));
        when(documentMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.search(USER_ID, "关键词");
        assertThat(result.get("requirements")).asList().hasSize(1);
        assertThat(result.get("bugs")).asList().isEmpty();
        assertThat(result.get("projects")).asList().isEmpty();
    }
}
