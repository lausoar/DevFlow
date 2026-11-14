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
}
