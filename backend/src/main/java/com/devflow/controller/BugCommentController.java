package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.BugComment;
import com.devflow.mapper.BugMapper;
import com.devflow.service.BugCommentService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Bug 评论控制器
 */
@Tag(name = "Bug评论", description = "Bug 评论的增删查（含 @提及通知）")
@RestController
@RequiredArgsConstructor
public class BugCommentController {

    private final BugCommentService bugCommentService;
    private final BugMapper bugMapper;
    private final ProjectAccessService projectAccessService;

    @Operation(summary = "获取 Bug 评论")
    @GetMapping("/bugs/{bugId}/comments")
    public R<List<BugComment>> listByBug(@PathVariable String bugId, Authentication authentication) {
        requireBugVisible(bugId, authentication);
        return R.ok(bugCommentService.listByBug(bugId));
    }

    @Operation(summary = "创建 Bug 评论（@项目成员会收到通知）")
    @PostMapping("/bugs/{bugId}/comments")
    public R<BugComment> create(@PathVariable String bugId,
                                @RequestBody Map<String, String> body,
                                Authentication authentication) {
        requireBugVisible(bugId, authentication);
        return R.ok(bugCommentService.create(bugId, body.get("content"),
                (String) authentication.getPrincipal()));
    }

    @Operation(summary = "删除 Bug 评论（仅本人或企业管理员以上）")
    @DeleteMapping("/bugs/comments/{commentId}")
    public R<Void> delete(@PathVariable String commentId, Authentication authentication) {
        bugCommentService.delete(commentId, (String) authentication.getPrincipal());
        return R.ok();
    }

    private void requireBugVisible(String bugId, Authentication authentication) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), bug.getProjectId())) {
            throw new BusinessException(404, "Bug 不存在或已被删除");
        }
    }
}
