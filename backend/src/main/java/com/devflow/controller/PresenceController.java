package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 在线状态接口
 * 页面初始加载时拉取一次在线快照，此后由 WebSocket /topic/presence 实时推送
 */
@Tag(name = "在线状态", description = "企业成员实时在线状态")
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @Operation(summary = "获取当前全部在线用户ID快照")
    @GetMapping
    public org.springframework.http.ResponseEntity<R<Map<String, List<String>>>> online(
            @RequestParam(required = false) String enterpriseId) {
        // enterpriseId 预留：当前广播为全量在线名单，由前端按企业成员过滤。
        // no-store：在线名单实时变化，浏览器不得缓存 GET 响应（否则快照会读到过期名单）
        R<Map<String, List<String>>> body = R.ok(Map.of("onlineUserIds",
                List.copyOf(presenceService.onlineUserIds())));
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(body);
    }
}
