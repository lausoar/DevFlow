package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.result.R;
import com.devflow.entity.FileItem;
import com.devflow.mapper.FileItemMapper;
import io.minio.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * 文件控制器 - MinIO 上传/下载
 */
@Tag(name = "文件管理", description = "文件上传、下载、预览")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileItemMapper fileItemMapper;
    private final MinioClient minioClient;

    @Value("${minio.bucket:devflow}")
    private String bucket;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<FileItem> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String projectId,
            Authentication authentication) {

        try {
            String userId = (String) authentication.getPrincipal();
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
            String objectName = UUID.randomUUID() + ext;

            // 确保 bucket 存在
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            // 上传到 MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );

            // 保存文件记录
            FileItem fileItem = new FileItem();
            fileItem.setName(originalName);
            fileItem.setSize(file.getSize());
            fileItem.setMimeType(file.getContentType());
            fileItem.setUrl("/files/download/" + objectName);
            fileItem.setUploaderId(userId);
            fileItem.setProjectId(projectId);
            fileItem.setVersion(1);
            fileItem.setDeleted(0);
            fileItemMapper.insert(fileItem);

            return R.ok(fileItem);
        } catch (Exception e) {
            return R.fail("文件上传失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取文件列表")
    @GetMapping
    public R<Page<FileItem>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String projectId) {

        Page<FileItem> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<FileItem> wrapper = new LambdaQueryWrapper<>();

        if (projectId != null && !projectId.isBlank()) {
            wrapper.eq(FileItem::getProjectId, projectId);
        }

        wrapper.orderByDesc(FileItem::getCreatedAt);
        return R.ok(fileItemMapper.selectPage(pageParam, wrapper));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        fileItemMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "下载/预览文件")
    @GetMapping("/download/{objectName:.+}")
    public void download(
            @PathVariable String objectName,
            @RequestParam(defaultValue = "false") boolean download,
            HttpServletResponse response) {

        try {
            // 查询文件记录以获取原始文件名与 MIME 类型
            FileItem item = fileItemMapper.selectOne(
                new LambdaQueryWrapper<FileItem>()
                    .eq(FileItem::getUrl, "/files/download/" + objectName)
                    .last("LIMIT 1")
            );
            String fileName = (item != null && item.getName() != null) ? item.getName() : objectName;
            String contentType = (item != null && item.getMimeType() != null)
                ? item.getMimeType() : "application/octet-stream";

            // 中文文件名编码（RFC 5987），兼顾浏览器兼容性
            String encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            String disposition = download ? "attachment" : "inline";
            response.setContentType(contentType);
            response.setHeader("Content-Disposition",
                disposition + "; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);

            // 从 MinIO 拉取文件流并写回响应
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectName).build());
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            // 文件不存在或读取失败时返回 404
            response.setStatus(HttpStatus.NOT_FOUND.value());
        }
    }
}
