package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.Requirement;
import com.devflow.entity.SavedFilter;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.SavedFilterMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.query.QueryExpressionParser;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 结构化查询 + 保存筛选器。
 *
 * 定位：给"我参与的项目里本周到期、P1、且 7 天没更新的 Bug"这类问题一个统一入口，
 * 看板、列表、导出、将来的仪表盘都复用同一份查询表达式。
 *
 * 安全：表达式不会变成 SQL —— 字段/运算符/函数全部白名单，值走参数绑定，
 * 可见项目由 ProjectAccessService 强制收口（表达式无法突破）。
 */
@Tag(name = "结构化查询", description = "JQL 式查询与保存的筛选器")
@RestController
@RequiredArgsConstructor
public class QueryController {

    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final SavedFilterMapper savedFilterMapper;
    private final ProjectAccessService projectAccessService;
    private final AccessGuard guard;

    /** 执行查询：返回分页结果 */
    @Data
    public static class QueryRequest {
        /** 查询对象: TASK / BUG / REQUIREMENT */
        private String entity;
        /** 结构化表达式，如 status IN (TODO, DOING) AND assignee = currentUser() */
        @Size(max = 2000, message = "表达式过长")
        private String expression;
        private Integer page = 1;
        private Integer pageSize = 50;
        /** 限定项目（可空=全部可见项目） */
        private String projectId;
    }

    @Operation(summary = "执行结构化查询")
    @PostMapping("/query")
    public R<Map<String, Object>> query(@RequestBody QueryRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);
        String entity = request.getEntity() == null ? "TASK" : request.getEntity().toUpperCase();

        List<String> projectIds = visibleProjects(userId, request.getProjectId());
        if (projectIds.isEmpty()) {
            return R.ok(Map.of("records", List.of(), "total", 0, "page", 1,
                    "pageSize", request.getPageSize(), "entity", entity));
        }

        QueryExpressionParser parser = new QueryExpressionParser(entity);
        QueryWrapper<?> wrapper = parser.toWrapper(request.getExpression(), userId, projectIds);
        wrapper.orderByDesc("created_at");

        int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 50
                : Math.min(request.getPageSize(), 200);

        Page<?> result = switch (entity) {
            case "BUG" -> bugMapper.selectPage(new Page<Bug>(page, pageSize),
                    (QueryWrapper<Bug>) wrapper);
            case "REQUIREMENT" -> requirementMapper.selectPage(new Page<Requirement>(page, pageSize),
                    (QueryWrapper<Requirement>) wrapper);
            case "TASK" -> taskMapper.selectPage(new Page<Task>(page, pageSize),
                    (QueryWrapper<Task>) wrapper);
            default -> throw new BusinessException(400, "不支持的查询对象: " + entity);
        };

        return R.ok(Map.of(
                "entity", entity,
                "records", result.getRecords(),
                "total", result.getTotal(),
                "page", result.getCurrent(),
                "pageSize", result.getSize()));
    }

    // ==================== 保存的筛选器 ====================

    @Operation(summary = "我的筛选器列表（含项目共享）")
    @GetMapping("/filters")
    public R<List<SavedFilter>> listFilters(@RequestParam(required = false) String projectId,
                                            @RequestParam(required = false) String entity,
                                            Authentication authentication) {
        String userId = guard.userId(authentication);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SavedFilter> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (StringUtils.hasText(projectId)) {
            guard.requireView(userId, projectId);
            wrapper.eq(SavedFilter::getProjectId, projectId);
        }
        if (StringUtils.hasText(entity)) {
            wrapper.eq(SavedFilter::getEntity, entity.toUpperCase());
        }
        wrapper.and(w -> w.eq(SavedFilter::getOwnerId, userId).or().eq(SavedFilter::getIsShared, 1));
        wrapper.orderByDesc(SavedFilter::getUpdatedAt);
        return R.ok(savedFilterMapper.selectList(wrapper));
    }

    @Operation(summary = "保存筛选器")
    @PostMapping("/filters")
    public R<SavedFilter> saveFilter(@RequestBody SavedFilter filter, Authentication authentication) {
        String userId = guard.userId(authentication);
        if (!StringUtils.hasText(filter.getName()) || !StringUtils.hasText(filter.getExpression())) {
            throw new BusinessException(400, "筛选器名称与表达式不能为空");
        }
        String entity = filter.getEntity() == null ? "TASK" : filter.getEntity().toUpperCase();
        if (!List.of("TASK", "BUG", "REQUIREMENT").contains(entity)) {
            throw new BusinessException(400, "不支持的查询对象");
        }
        // 先解析一次：表达式非法就不该被保存（避免"存下来但永远跑不通"）
        new QueryExpressionParser(entity).parse(filter.getExpression());

        if (StringUtils.hasText(filter.getProjectId())) {
            guard.requireProjectPermission(userId, filter.getProjectId(), PermissionCodes.PROJECT_EDIT);
        }

        filter.setId(null);
        filter.setOwnerId(userId);
        filter.setEntity(entity);
        filter.setDeleted(0);
        if (filter.getIsShared() == null) {
            filter.setIsShared(0);
        }
        savedFilterMapper.insert(filter);
        return R.ok(filter);
    }

    @Operation(summary = "删除筛选器（仅本人）")
    @DeleteMapping("/filters/{id}")
    public R<Void> deleteFilter(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        SavedFilter filter = savedFilterMapper.selectById(id);
        if (filter == null) {
            throw new BusinessException(404, "筛选器不存在");
        }
        if (!userId.equals(filter.getOwnerId())) {
            throw new BusinessException(403, "只能删除自己保存的筛选器");
        }
        savedFilterMapper.deleteById(id);
        return R.ok();
    }

    /** 可见项目：指定项目时校验可见性，否则取全部可见项目 */
    private List<String> visibleProjects(String userId, String projectId) {
        if (StringUtils.hasText(projectId)) {
            guard.requireView(userId, projectId);
            return List.of(projectId);
        }
        return projectAccessService.visibleProjectIds(userId);
    }
}
