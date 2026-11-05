package com.devflow.service.impl;

import com.devflow.entity.Enterprise;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.service.ImPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * 企业微信机器人推送实现（Webhook JSON API）。
 * 推送异步执行；单次超时 5 秒；失败仅记日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImPushServiceImpl implements ImPushService {

    private final EnterpriseMapper enterpriseMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 企业微信 Markdown 消息上限 4096 字节（UTF-8），留余量 */
    private static final int MAX_BYTES = 4000;

    @Override
    public void pushMarkdown(String enterpriseId, String markdown) {
        CompletableFuture.runAsync(() -> {
            try {
                sendSync(enterpriseId, markdown);
            } catch (Exception e) {
                log.warn("企业微信推送失败（不影响主流程）enterprise={}: {}", enterpriseId, e.getMessage());
            }
        });
    }

    @Override
    public Map<String, Object> sendTest(String enterpriseId) {
        Map<String, Object> result = new HashMap<>();
        String webhook = resolveWebhook(enterpriseId);
        if (webhook == null) {
            result.put("ok", false);
            result.put("error", "尚未配置企业微信机器人 Webhook");
            return result;
        }
        try {
            sendSync(enterpriseId, "**DevFlow 测试消息**\n配置成功，关键事件将推送到本群。");
            result.put("ok", true);
            result.put("error", null);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ==================== 内部 ====================

    private String resolveWebhook(String enterpriseId) {
        if (!StringUtils.hasText(enterpriseId)) {
            return null;
        }
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || !StringUtils.hasText(enterprise.getWecomWebhook())) {
            return null;
        }
        return enterprise.getWecomWebhook();
    }

    /** 同步发送；失败抛异常（调用方决定是否兜底） */
    private void sendSync(String enterpriseId, String markdown) throws Exception {
        String webhook = resolveWebhook(enterpriseId);
        if (webhook == null) {
            throw new IllegalStateException("未配置企业微信机器人 Webhook");
        }

        String content = truncateToUtf8Bytes(markdown, MAX_BYTES);
        String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                Map.of("msgtype", "markdown", "markdown", Map.of("content", content)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("企业微信接口返回 " + resp.statusCode());
        }
        // 企业微信业务错误在 body：{"errcode":非0}
        if (resp.body().contains("\"errcode\":") && !resp.body().matches(".*\"errcode\"\\s*:\\s*0.*")) {
            throw new IllegalStateException("企业微信返回错误: " + resp.body());
        }
    }

    /** 按UTF-8字节数截断，避免切坏多字节字符 */
    private String truncateToUtf8Bytes(String text, int maxBytes) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
