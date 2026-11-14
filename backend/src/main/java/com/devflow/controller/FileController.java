package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.FileItem;
import com.devflow.entity.User;
import com.devflow.mapper.FileItemMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import io.minio.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件控制器 - MinIO 上传/下载
 *
 * 安全口径（见 docs/AUDIT_REPORT_2026-11.md §A6）：
 * - 上传/列表/删除/下载都必须带项目归属校验（项目文档的附件按项目隔离）；
 * - 上传按扩展名白名单 + 服务端推导 MIME，浏览器不会把上传内容当页面执行；
 * - 下载一律以 attachment + nosniff 下发，杜绝同源存储型 XSS。
 */
@Slf4j
@Tag(name = "文件管理", description = "文件上传、下载、预览")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileItemMapper fileItemMapper;
    private final UserMapper userMapper;
    private final MinioClient minioClient;
    private final AccessGuard guard;
    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;

    @Value("${minio.bucket:devflow}")
    private String bucket;

    /** 允许上传的扩展名 → 服务端推导的 MIME（不信任客户端声明的 Content-Type） */
    private static final java.util.Map<String, String> ALLOWED_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("webp", "image/webp"),
            java.util.Map.entry("bmp", "image/bmp"),
            java.util.Map.entry("svg", "image/svg+xml"),
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("txt", "text/plain"),
            java.util.Map.entry("md", "text/plain"),
            java.util.Map.entry("csv", "text/csv"),
            java.util.Map.entry("json", "application/json"),
            java.util.Map.entry("xml", "application/xml"),
            java.util.Map.entry("log", "text/plain"),
            java.util.Map.entry("zip", "application/zip"),
            java.util.Map.entry("7z", "application/x-7z-compressed"),
            java.util.Map.entry("rar", "application/vnd.rar"),
            java.util.Map.entry("tar", "application/x-tar"),
            java.util.Map.entry("gz", "application/gzip"),
            java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("ppt", "application/vnd.ms-powerpoint"),
            java.util.Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            java.util.Map.entry("mp4", "video/mp4"),
            java.util.Map.entry("mp3", "audio/mpeg"),
            java.util.Map.entry("wav", "audio/wav"),
            java.util.Map.entry("har", "application/json"),
            java.util.Map.entry("apk", "application/vnd.android.package-archive"));

    /** 永远不允许的扩展名（可执行/脚本/服务端页面），即使出现在白名单之外也显式拒绝 */
    private static final Set<String> DENIED_EXT = Set.of(
            "html", "htm", "xhtml", "shtml", "js", "mjs", "exe", "bat", "cmd", "sh", "ps1",
            "jsp", "jspx", "php", "asp", "aspx", "jar", "war", "class", "dll", "so");

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<FileItem> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String projectId,
            Authentication authentication) {

        String userId = guard.userId(authentication);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            throw new BusinessException(400, "文件名不合法");
        }
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!StringUtils.hasText(ext) || DENIED_EXT.contains(ext) || !ALLOWED_TYPES.containsKey(ext)) {
            throw new BusinessException(400, "不支持的文件类型，仅允许图片/文档/压缩包/音视频等常见格式");
        }
        String contentType = ALLOWED_TYPES.get(ext);

        // 项目归属：附件挂在项目下时需该项目可见 + file:manage
        if (StringUtils.hasText(projectId)) {
            guard.requireProjectPermission(userId, projectId, PermissionCodes.FILE_MANAGE);
        }

        try {
            String objectName = UUID.randomUUID() + "." + ext;

            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );

            FileItem fileItem = new FileItem();
            fileItem.setName(originalName);
            fileItem.setSize(file.getSize());
            fileItem.setMimeType(contentType);
            fileItem.setUrl("/files/download/" + objectName);
            fileItem.setUploaderId(userId);
            fileItem.setProjectId(StringUtils.hasText(projectId) ? projectId : null);
            fileItem.setVersion(1);
            fileItem.setDeleted(0);
            fileItemMapper.insert(fileItem);
            return R.ok(fileItem);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("文件上传失败: {}", e.getMessage());
            throw new BusinessException(500, "文件上传失败，请稍后重试");
        }
    }

    @Operation(summary = "获取文件列表")
    @GetMapping
    public R<Page<FileItem>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String projectId,
            Authentication authentication) {

        String userId = guard.userId(authentication);
        Page<FileItem> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<FileItem> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(projectId)) {
            guard.requireView(userId, projectId);
            wrapper.eq(FileItem::getProjectId, projectId);
        } else {
            // 无项目过滤：默认只看自己上传的文件，避免一览全企业文件
            wrapper.eq(FileItem::getUploaderId, userId);
        }

        wrapper.orderByDesc(FileItem::getCreatedAt);
        return R.ok(fileItemMapper.selectPage(pageParam, wrapper));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        FileItem item = fileItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "文件不存在");
        }
        requireFileManage(userId, item);
        // 逻辑删除：保留 MinIO 对象以便恢复，物理清理交由后续保留策略任务
        fileItemMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "下载/预览文件")
    @GetMapping("/download/{objectName:.+}")
    public void download(
            @PathVariable String objectName,
            @RequestParam(defaultValue = "false") boolean download,
            Authentication authentication,
            HttpServletResponse response) {

        String userId = guard.userId(authentication);
        FileItem item = fileItemMapper.selectOne(
            new LambdaQueryWrapper<FileItem>()
                .eq(FileItem::getUrl, "/files/download/" + objectName)
                .last("LIMIT 1")
        );
        if (item == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        // 归属校验：项目附件按项目可见性，无项目文件按上传者/同企业
        boolean allowed = false;
        if (StringUtils.hasText(item.getProjectId())) {
            allowed = projectAccessService.canView(userId, item.getProjectId());
        } else {
            User me = userMapper.selectById(userId);
            String myEnterpriseId = me != null ? me.getEnterpriseId() : null;
            User uploader = userMapper.selectById(item.getUploaderId());
            String uploaderEnterpriseId = uploader != null ? uploader.getEnterpriseId() : null;
            allowed = userId.equals(item.getUploaderId())
                    || (myEnterpriseId != null && myEnterpriseId.equals(uploaderEnterpriseId));
        }
        if (!allowed) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        try {
            String fileName = item.getName() != null ? item.getName() : objectName;
            // MIME 一律按白名单映射后的值下发，不信任库里的历史脏值（旧数据可能是客户端上报的 text/html）
            String contentType = resolveSafeContentType(fileName, item.getMimeType());

            String encodedFallback = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            // 一律 attachment 下发：内联渲染会被用于同源 XSS（HTML/SVG）
            response.setContentType(contentType);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodedFallback + "\"; filename*=UTF-8''" + encodedFallback);
            // 预览场景由 <img> 提供，不需要浏览器解析脚本
            response.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; sandbox");

            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectName).build());
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
    }

    // ============================================
    // 内部
    // ============================================

    /** 删除权限：项目文件要求 file:manage；无项目文件仅上传者本人或企业管理员可删 */
    private void requireFileManage(String userId, FileItem item) {
        if (StringUtils.hasText(item.getProjectId())) {
            if (!permissionService.hasProjectPermission(userId, item.getProjectId(), PermissionCodes.FILE_MANAGE)) {
                throw new BusinessException(403, "无权删除该项目文件");
            }
            return;
        }
        if (userId.equals(item.getUploaderId())) {
            return;
        }
        User me = userMapper.selectById(userId);
        User uploader = userMapper.selectById(item.getUploaderId());
        boolean sameEnterprise = me != null && uploader != null
                && StringUtils.hasText(me.getEnterpriseId())
                && me.getEnterpriseId().equals(uploader.getEnterpriseId());
        boolean admin = me != null
                && com.devflow.common.EnterpriseRoles.level(me.getRole())
                >= com.devflow.common.EnterpriseRoles.level(com.devflow.common.EnterpriseRoles.ADMIN);
        if (!(sameEnterprise && admin)) {
            throw new BusinessException(403, "只能删除自己上传的文件");
        }
    }

    /** 扩展名 → 白名单 MIME；未知扩展名回落到 octet-stream（永不返回 text/html） */
    private String resolveSafeContentType(String fileName, String storedMime) {
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT)
                : "";
        String mapped = ALLOWED_TYPES.get(ext);
        if (mapped != null) {
            return mapped;
        }
        if (StringUtils.hasText(storedMime) && !storedMime.toLowerCase(Locale.ROOT).contains("html")
                && !storedMime.toLowerCase(Locale.ROOT).contains("javascript")) {
            return storedMime;
        }
        return "application/octet-stream";
    }
}
