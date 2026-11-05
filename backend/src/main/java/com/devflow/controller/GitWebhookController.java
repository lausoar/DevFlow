package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.GitCommit;
import com.devflow.entity.Project;
import com.devflow.mapper.GitCommitMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.service.ImPushService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Git 集成（最小自洽版）：
 * - 项目设置里填仓库地址 + Webhook 地址（带密钥）
 * - 代码托管平台（GitHub/GitLab/Gitee 等）配置 push webhook 指到本端点
 * - 提交落库去重，并推送摘要到企业微信群
 */
@Slf4j
@Tag(name = "Git 集成", description = "Git push webhook 接入与提交记录")
@RestController
@RequiredArgsConstructor
public class GitWebhookController {

    private final ProjectMapper projectMapper;
    private final GitCommitMapper gitCommitMapper;
    private final ImPushService imPushService;
    private final ProjectAccessService projectAccessService;

    /**
     * push webhook 入站（免登录，用项目级密钥鉴权）。
     * 兼容 GitHub / GitLab / Gitee 的 push 事件 JSON：解析顶层 commits 数组。
     * 鉴权失败返回真实 HTTP 401（外部平台依赖状态码判断配置是否正确）。
     */
    @Operation(summary = "Git push webhook 入站")
    @PostMapping("/projects/{projectId}/git/webhook")
    public R<Map<String, Object>> webhook(@PathVariable String projectId,
                                          @RequestParam(required = false) String secret,
                                          @RequestBody Map<String, Object> body,
                                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeleted() != null && project.getDeleted() == 1) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"message\":\"项目不存在\"}");
            return null;
        }
        String expected = project.getGitWebhookSecret();
        if (expected == null || expected.isBlank() || !expected.equals(secret)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Webhook 密钥无效\"}");
            return null;
        }

        Object commitsObj = body.get("commits");
        if (!(commitsObj instanceof List<?> commits) || commits.isEmpty()) {
            // 非 push 事件（ping/branch 删除等）直接确认
            return R.ok(Map.of("stored", 0));
        }

        String pusherName = stringValue(body.get("pusher") instanceof Map<?, ?> p ? p.get("name") : null);
        if (pusherName == null) {
            pusherName = stringValue(body.get("user_name"));
        }

        int stored = 0;
        List<String> lines = new ArrayList<>();
        for (Object o : commits) {
            if (!(o instanceof Map<?, ?> c)) continue;
            String sha = stringValue(firstNonNull(c.get("id"), c.get("sha")));
            if (sha == null || sha.isBlank()) continue;
            String message = stringValue(c.get("message"));
            String author = stringValue(firstNonNull(
                    c.get("author") instanceof Map<?, ?> a ? a.get("name") : c.get("author"),
                    pusherName));
            String url = stringValue(firstNonNull(c.get("url"), c.get("html_url")));

            // 同项目同 SHA 去重
            Long count = gitCommitMapper.selectCount(new LambdaQueryWrapper<GitCommit>()
                    .eq(GitCommit::getProjectId, projectId)
                    .eq(GitCommit::getSha, sha));
            if (count != null && count > 0) continue;

            GitCommit commit = new GitCommit();
            commit.setProjectId(projectId);
            commit.setSha(sha);
            commit.setMessage(truncate(message, 1000));
            commit.setAuthorName(truncate(author, 100));
            commit.setWebUrl(url);
            commit.setPushedAt(LocalDateTime.now());
            try {
                gitCommitMapper.insert(commit);
                stored++;
            } catch (Exception e) {
                log.warn("git commit 落库失败 sha={}: {}", sha, e.getMessage());
            }
            lines.add("- `" + sha.substring(0, Math.min(7, sha.length())) + "` " +
                    truncate(firstLine(message), 80) + (author != null ? "（" + author + "）" : ""));
        }

        // 推送摘要到企业微信群（有推送内容才推）
        if (stored > 0 && project.getEnterpriseId() != null) {
            StringBuilder md = new StringBuilder();
            md.append("**📦 ").append(project.getName()).append(" 代码推送**");
            if (pusherName != null) md.append(" — ").append(pusherName);
            md.append("\n").append(stored).append(" 个新提交：\n");
            for (int i = 0; i < Math.min(lines.size(), 5); i++) {
                md.append(lines.get(i)).append("\n");
            }
            if (lines.size() > 5) md.append("... 等共 ").append(lines.size()).append(" 个提交");
            imPushService.pushMarkdown(project.getEnterpriseId(), md.toString());
        }
        return R.ok(Map.of("stored", stored));
    }

    /** 项目最近提交（项目成员可见） */
    @Operation(summary = "项目最近提交")
    @GetMapping("/projects/{projectId}/git/commits")
    public R<List<GitCommit>> commits(@PathVariable String projectId, Authentication authentication) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return R.ok(gitCommitMapper.selectList(new LambdaQueryWrapper<GitCommit>()
                .eq(GitCommit::getProjectId, projectId)
                .orderByDesc(GitCommit::getPushedAt)
                .last("LIMIT 20")));
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String stringValue(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int idx = s.indexOf('\n');
        return idx > 0 ? s.substring(0, idx) : s;
    }
}
