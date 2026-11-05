package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.entity.Document;
import com.devflow.mapper.DocumentMapper;
import com.devflow.service.AiKnowledgeSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档控制器
 */
@Tag(name = "文档管理", description = "文档 CRUD、Wiki 树形结构")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentMapper documentMapper;
    private final AiKnowledgeSyncService aiKnowledgeSyncService;
    private final ProjectAccessService projectAccessService;
    private final com.devflow.mapper.DocumentVersionMapper documentVersionMapper;
    private final com.devflow.mapper.DocumentCommentMapper documentCommentMapper;
    private final com.devflow.mapper.UserMapper userMapper;

    @Operation(summary = "获取文档列表")
    @GetMapping
    public R<List<Document>> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String type,
            Authentication authentication) {

        // 带项目维度查询时按项目可见性收口
        if (projectId != null && !projectId.isBlank()
                && !projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }

        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();

        if (projectId != null && !projectId.isBlank()) {
            wrapper.eq(Document::getProjectId, projectId);
        } else {
            // 全局文档（无项目归属）按企业隔离：仅本企业的全局文档可见；无企业用户看不到全局文档
            com.devflow.entity.User requester = userMapper.selectById((String) authentication.getPrincipal());
            String myEnterpriseId = requester != null ? requester.getEnterpriseId() : null;
            if (myEnterpriseId == null || myEnterpriseId.isBlank()) {
                return R.ok(List.of());
            }
            wrapper.isNull(Document::getProjectId).eq(Document::getEnterpriseId, myEnterpriseId);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(Document::getType, type);
        }

        wrapper.orderByAsc(Document::getSort).orderByDesc(Document::getUpdatedAt);
        return R.ok(documentMapper.selectList(wrapper));
    }

    @Operation(summary = "获取文档详情")
    @GetMapping("/{id}")
    public R<Document> getById(@PathVariable String id, Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        return R.ok(doc);
    }

    @Operation(summary = "创建文档")
    @PostMapping
    public R<Document> create(@RequestBody Document document, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        document.setAuthorId(userId);
        // 记录创建企业：全局文档按企业隔离的依据
        com.devflow.entity.User creator = userMapper.selectById(userId);
        if (creator != null) {
            document.setEnterpriseId(creator.getEnterpriseId());
        }
        if (document.getType() == null) {
            document.setType("DOC");
        }
        if (document.getSort() == null) {
            document.setSort(0);
        }
        if (document.getIsPublished() == null) {
            document.setIsPublished(0);
        }
        document.setDeleted(0);
        documentMapper.insert(document);
        aiKnowledgeSyncService.indexDocument(document);
        return R.ok(document);
    }

    @Operation(summary = "更新文档")
    @PutMapping("/{id}")
    public R<Document> update(@PathVariable String id, @RequestBody Document document,
                              Authentication authentication) {
        Document old = documentMapper.selectById(id);
        if (old == null || !docVisible(old, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }

        // 保存前对旧内容做版本快照（快照版本号 = 旧文档版本号，新版本号 = 旧 + 1）
        int prevVersion = old.getVersion() == null ? 1 : old.getVersion();
        com.devflow.entity.DocumentVersion snapshot = new com.devflow.entity.DocumentVersion();
        snapshot.setDocumentId(id);
        snapshot.setVersion(prevVersion);
        snapshot.setTitle(old.getTitle());
        snapshot.setContent(old.getContent());
        snapshot.setEditorId((String) authentication.getPrincipal());
        documentVersionMapper.insert(snapshot);

        document.setId(id);
        document.setVersion(prevVersion + 1);
        documentMapper.updateById(document);
        // 重新读取合并后的文档（更新请求可能只携带部分字段）再同步知识库
        Document merged = documentMapper.selectById(id);
        aiKnowledgeSyncService.indexDocument(merged);
        return R.ok(merged);
    }

    // ==================== 版本历史 ====================

    /** 版本列表（含编辑人展示名，不含大字段 content） */
    @Operation(summary = "获取文档版本列表")
    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(@PathVariable String id, Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        return R.ok(documentVersionMapper.selectVersionList(id));
    }

    /** 版本详情（含 content，用于预览） */
    @Operation(summary = "获取版本详情")
    @GetMapping("/{id}/versions/{versionId}")
    public R<com.devflow.entity.DocumentVersion> versionDetail(@PathVariable String id,
                                                               @PathVariable String versionId,
                                                               Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        com.devflow.entity.DocumentVersion v = documentVersionMapper.selectById(versionId);
        if (v == null || !v.getDocumentId().equals(id)) {
            throw new BusinessException(404, "版本不存在");
        }
        return R.ok(v);
    }

    /** 恢复版本：先快照当前内容，再用所选版本覆盖（标题+内容），版本号继续递增 */
    @Operation(summary = "恢复到指定版本")
    @PostMapping("/{id}/versions/{versionId}/restore")
    public R<Document> restoreVersion(@PathVariable String id,
                                      @PathVariable String versionId,
                                      Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        com.devflow.entity.DocumentVersion v = documentVersionMapper.selectById(versionId);
        if (v == null || !v.getDocumentId().equals(id)) {
            throw new BusinessException(404, "版本不存在");
        }

        int prevVersion = doc.getVersion() == null ? 1 : doc.getVersion();
        com.devflow.entity.DocumentVersion snapshot = new com.devflow.entity.DocumentVersion();
        snapshot.setDocumentId(id);
        snapshot.setVersion(prevVersion);
        snapshot.setTitle(doc.getTitle());
        snapshot.setContent(doc.getContent());
        snapshot.setEditorId((String) authentication.getPrincipal());
        documentVersionMapper.insert(snapshot);

        doc.setTitle(v.getTitle());
        doc.setContent(v.getContent());
        doc.setVersion(prevVersion + 1);
        documentMapper.updateById(doc);
        aiKnowledgeSyncService.indexDocument(doc);
        return R.ok(doc);
    }

    // ==================== 文档评论 ====================

    @Operation(summary = "获取文档评论")
    @GetMapping("/{id}/comments")
    public R<List<Map<String, Object>>> comments(@PathVariable String id, Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        return R.ok(documentCommentMapper.selectCommentList(id));
    }

    @Operation(summary = "发表文档评论")
    @PostMapping("/{id}/comments")
    public R<Map<String, Object>> addComment(@PathVariable String id,
                                             @RequestBody Map<String, String> body,
                                             Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        String content = body.getOrDefault("content", "").trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BusinessException(400, "评论不能超过 2000 字");
        }
        com.devflow.entity.DocumentComment comment = new com.devflow.entity.DocumentComment();
        comment.setDocumentId(id);
        comment.setUserId((String) authentication.getPrincipal());
        comment.setContent(content);
        documentCommentMapper.insert(comment);

        com.devflow.entity.User user = userMapper.selectById(comment.getUserId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", comment.getId());
        result.put("documentId", id);
        result.put("userId", comment.getUserId());
        result.put("content", content);
        result.put("userName", user != null ? user.displayName() : null);
        result.put("userAvatar", user != null ? user.getAvatar() : null);
        return R.ok(result);
    }

    @Operation(summary = "删除文档评论（仅本人）")
    @DeleteMapping("/{id}/comments/{commentId}")
    public R<Void> deleteComment(@PathVariable String id,
                                 @PathVariable String commentId,
                                 Authentication authentication) {
        Document doc = documentMapper.selectById(id);
        if (doc == null || !docVisible(doc, authentication)) {
            throw new BusinessException(404, "文档不存在或已被删除");
        }
        com.devflow.entity.DocumentComment comment = documentCommentMapper.selectById(commentId);
        if (comment == null || !comment.getDocumentId().equals(id)) {
            throw new BusinessException(404, "评论不存在");
        }
        if (!comment.getUserId().equals(authentication.getPrincipal())) {
            throw new BusinessException(403, "只能删除自己的评论");
        }
        documentCommentMapper.deleteById(commentId);
        return R.ok();
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        documentMapper.deleteById(id);
        aiKnowledgeSyncService.deleteDocument(id);
        return R.ok();
    }

    /**
     * 获取 Wiki 树形结构
     * 返回所有文档，前端自行构建树
     */
    @Operation(summary = "获取项目 Wiki 树形结构")
    @GetMapping("/wiki/tree")
    public R<List<WikiTreeNode>> wikiTree(@RequestParam String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getProjectId, projectId)
                .eq(Document::getType, "WIKI")
                .orderByAsc(Document::getSort)
        );

        // 构建树形结构
        List<WikiTreeNode> tree = buildWikiTree(docs, null);
        return R.ok(tree);
    }

    // ============================================
    // 内部方法
    // ============================================

// ==================== 内部 ====================

    /** 文档可见性：项目归属文档按项目成员判定；全局文档仅作者同企业可见 */
    private boolean docVisible(Document doc, Authentication authentication) {
        String pid = doc.getProjectId();
        if (pid != null && !pid.isBlank()) {
            return projectAccessService.canView((String) authentication.getPrincipal(), pid);
        }
        com.devflow.entity.User user = userMapper.selectById((String) authentication.getPrincipal());
        String myEnterpriseId = user != null ? user.getEnterpriseId() : null;
        return myEnterpriseId != null && myEnterpriseId.equals(doc.getEnterpriseId());
    }


    private List<WikiTreeNode> buildWikiTree(List<Document> docs, String parentId) {
        return docs.stream()
            .filter(d -> {
                if (parentId == null) return d.getParentId() == null;
                return parentId.equals(d.getParentId());
            })
            .map(d -> {
                WikiTreeNode node = new WikiTreeNode();
                node.setId(d.getId());
                node.setTitle(d.getTitle());
                node.setSort(d.getSort());
                node.setIsPublished(d.getIsPublished() == 1);
                node.setChildren(buildWikiTree(docs, d.getId()));
                return node;
            })
            .collect(Collectors.toList());
    }

    // ============================================
    // Wiki 树节点 DTO
    // ============================================
    @lombok.Data
    public static class WikiTreeNode {
        private String id;
        private String title;
        private Integer sort;
        private Boolean isPublished;
        private List<WikiTreeNode> children;
    }
}
