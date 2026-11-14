package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.security.AccessGuard;
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
 *
 * 测试推送会向企业群发消息，必须限定在本企业且有管理权限，避免任意登录用户刷群。
 */
@Tag(name = "企业微信机器人", description = "Webhook 配置与测试推送")
@RestController
@RequestMapping("/enterprises/{id}/wecom")
@RequiredArgsConstructor
public class WeComBotController {

    private final ImPushService imPushService;
    private final AccessGuard guard;

    @Operation(summary = "发送测试消息到企业群")
    @PostMapping("/test")
    public R<Map<String, Object>> test(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(imPushService.sendTest(id));
    }
}
