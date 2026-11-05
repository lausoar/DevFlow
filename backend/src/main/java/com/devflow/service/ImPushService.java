package com.devflow.service;

import java.util.Map;

/**
 * 企业微信群机器人出站推送。
 * Webhook 配置在 enterprise.wecom_webhook（空 = 不推送）。
 * 所有推送均为异步且失败静默（记日志），不影响主流程。
 */
public interface ImPushService {

    /**
     * 推送 Markdown 消息到企业群（异步）。
     *
     * @param enterpriseId 企业（决定推到哪个群）
     * @param markdown     内容（企业微信 Markdown 语法，超 4096 字节截断）
     */
    void pushMarkdown(String enterpriseId, String markdown);

    /**
     * 同步发送测试消息（企业设置页「测试推送」按钮用）。
     *
     * @return {ok: 是否成功, error: 失败原因（成功为 null）}
     */
    Map<String, Object> sendTest(String enterpriseId);
}
