package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.security.AccessGuard;
import com.devflow.service.TriageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分诊收件箱。
 * 读 = 项目可见；处理 = 仅测试人员/项目管理员（Service 内按 bug:edit 判定）。
 */
@Tag(name = "分诊收件箱", description = "外部提交的接受/拒绝/合并与首响时限")
@RestController
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;
    private final AccessGuard guard;

    @Operation(summary = "项目分诊收件箱")
    @GetMapping("/projects/{projectId}/triage")
    public R<List<Map<String, Object>>> list(@PathVariable String projectId,
                                             @RequestParam(required = false) String status,
                                             Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(triageService.list(projectId, status));
    }

    @Operation(summary = "分诊计数")
    @GetMapping("/projects/{projectId}/triage/counts")
    public R<Map<String, Long>> counts(@PathVariable String projectId, Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(triageService.counts(projectId));
    }

    @Operation(summary = "相似缺陷提示（仅提示，不自动合并）")
    @GetMapping("/triage/{id}/similar")
    public R<List<Map<String, Object>>> similar(@PathVariable String id, Authentication authentication) {
        guard.userId(authentication);
        return R.ok(triageService.similar(id));
    }

    @Operation(summary = "接受")
    @PostMapping("/triage/{id}/accept")
    public R<Map<String, Object>> accept(@PathVariable String id, Authentication authentication) {
        return R.ok(triageService.accept(id, guard.userId(authentication)));
    }

    @Operation(summary = "拒绝")
    @PostMapping("/triage/{id}/decline")
    public R<Map<String, Object>> decline(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, String> body,
                                          Authentication authentication) {
        String reason = body != null ? body.get("reason") : null;
        return R.ok(triageService.decline(id, guard.userId(authentication), reason));
    }

    @Operation(summary = "合并到已有缺陷")
    @PostMapping("/triage/{id}/merge")
    public R<Map<String, Object>> merge(@PathVariable String id,
                                        @RequestBody Map<String, String> body,
                                        Authentication authentication) {
        return R.ok(triageService.merge(id, body.get("targetBugId"), guard.userId(authentication)));
    }
}
