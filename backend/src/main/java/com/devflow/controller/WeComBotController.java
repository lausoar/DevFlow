package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.service.ImPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业微信机器人推送：配置在企业设置（仅超级管理员），
 * 关键事件（新建缺陷/用例失败/转缺陷）自动出站推送。
 */
@Tag(name = "企业微信机器人", description = "Webhook 配置与测试推送")
@RestController
@RequestMapping("/enterprises/{id}/wecom")
@RequiredArgsConstructor
public class WeComBotController {

    private final ImPushService imPushService;

    @Operation(summary = "发送测试消息到企业群")
    @PostMapping("/test")
    public R<Map<String, Object>> test(@PathVariable String id, Authentication authentication) {
        return R.ok(imPushService.sendTest(id));
    }
}
