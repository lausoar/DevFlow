package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.dto.request.ChangePasswordRequest;
import com.devflow.entity.User;
import com.devflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户个人信息控制器
 */
@Tag(name = "用户信息", description = "个人信息修改、密码变更、头像更新")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取当前用户信息 */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<User> getCurrentUser(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(userService.getById(userId));
    }

    /** 修改个人信息（昵称、头像、手机号） */
    @Operation(summary = "修改个人信息")
    @PutMapping("/me")
    public R<User> updateProfile(@RequestBody User updates,
                                  Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(userService.updateProfile(userId, updates));
    }

    /** 修改密码 */
    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                   Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }

    /** 上传/更新头像 */
    @Operation(summary = "更新头像")
    @PutMapping("/me/avatar")
    public R<User> updateAvatar(@RequestBody Map<String, String> body,
                                 Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String avatarUrl = body.get("avatarUrl");
        return R.ok(userService.updateAvatar(userId, avatarUrl));
    }
}
