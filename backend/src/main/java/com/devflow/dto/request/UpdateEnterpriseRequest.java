package com.devflow.dto.request;

import lombok.Data;

/**
 * 更新企业信息请求
 */
@Data
public class UpdateEnterpriseRequest {

    /** 企业名称（可选） */
    private String name;

    /** 企业微信群机器人 Webhook（可选） */
    private String wecomWebhook;

    /** 企业 Logo URL（可选，传 null 表示不修改） */
    private String logo;

    /** 是否重新生成邀请码 */
    private Boolean regenerateInviteCode;
}
