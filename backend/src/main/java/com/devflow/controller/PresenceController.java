package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.PresenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication;
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
    private final UserMapper userMapper;
    private final AccessGuard guard;

    @Operation(summary = "获取当前在线用户ID快照（仅本企业）")
    @GetMapping
    public org.springframework.http.ResponseEntity<R<Map<String, List<String>>>> online(
            Authentication authentication) {
        // 在线名单按企业收敛：跨企业暴露在线状态属于组织信息泄露
        String myEnterpriseId = guard.enterpriseIdOf(guard.userId(authentication));
        List<String> online = presenceService.onlineUserIds().stream()
                .filter(id -> {
                    User u = userMapper.selectById(id);
                    return u != null && myEnterpriseId != null
                            && myEnterpriseId.equals(u.getEnterpriseId());
                })
                .toList();
        // no-store：在线名单实时变化，浏览器不得缓存 GET 响应（否则快照会读到过期名单）
        R<Map<String, List<String>>> body = R.ok(Map.of("onlineUserIds", online));
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(body);
    }
}
