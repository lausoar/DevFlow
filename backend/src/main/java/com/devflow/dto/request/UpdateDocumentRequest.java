package com.devflow.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新文档请求（白名单字段）。
 * projectId / authorId / enterpriseId / version / deleted 不允许由客户端改写。
 */
@Data
public class UpdateDocumentRequest {

    @Size(max = 200, message = "文档标题不能超过 200 字")
    private String title;

    @Size(max = 200000, message = "文档内容过长")
    private String content;

    /** 类型: DOC / WIKI / MEETING / REPORT */
    private String type;

    private Integer sort;
    private String parentId;
    private Integer isPublished;

    /**
     * 客户端读到的版本号（乐观锁）。传了就做冲突检测，不传则按旧行为直接保存。
     *
     * 为什么需要：原来是"读 → 快照 → version+1 写入"，全程不看客户端版本。
     * 两人同时编辑同一篇文档时，后者会静默覆盖前者的正文；如果两次插入撞上
     * uk_doc_ver 唯一键，接口直接 500 而不是提示冲突。用户看到的是"保存失败"，
     * 却不知道自己的内容已经丢了。
     */
    private Integer expectedVersion;
}
