package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 Bug 请求（白名单字段：状态由服务端按是否已指派推导，报告人取当前登录用户）
 */
@Data
public class CreateBugRequest {

    @NotBlank(message = "Bug 标题不能为空")
    @Size(max = 200, message = "Bug 标题不能超过 200 字")
    private String title;

    @Size(max = 20000, message = "Bug 描述过长")
    private String description;

    private String assigneeId;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 严重程度: BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL */
    private String severity;

    @Size(max = 200, message = "环境信息过长")
    private String environment;

    /** 截图 JSON 数组字符串 */
    @Size(max = 20000, message = "截图数据过长")
    private String screenshots;
}
