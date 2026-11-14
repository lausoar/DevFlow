package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建文档请求（白名单字段：authorId/enterpriseId/version 由服务端决定）
 */
@Data
public class CreateDocumentRequest {

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题不能超过 200 字")
    private String title;

    @Size(max = 200000, message = "文档内容过长")
    private String content;

    /** 所属项目（为空表示全局文档，按企业可见） */
    private String projectId;

    /** 父文档 ID（Wiki 树） */
    private String parentId;

    /** 类型: DOC / WIKI / MEETING / REPORT */
    private String type;

    private Integer sort;
}
