package com.devflow.service.impl;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 * 处理用户个人信息修改和密码变更
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User getById(String id) {
        return userMapper.selectById(id);
    }

    @Override
    public User updateProfile(String userId, User updates) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 只允许修改特定字段
        if (updates.getUsername() != null) {
            user.setUsername(updates.getUsername());
        }
        if (updates.getRealName() != null) {
            // 空串而非 null：updateById 跳过 null，置 null 无法清空（聊天 sender_name 的 COALESCE 兼容空串）
            user.setRealName(updates.getRealName().trim());
        }
        if (updates.getAvatar() != null) {
            user.setAvatar(updates.getAvatar());
        }
        if (updates.getPhone() != null) {
            user.setPhone(updates.getPhone());
        }

        userMapper.updateById(user);
        return userMapper.selectById(userId);
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户 {} 修改密码成功", userId);
    }

    @Override
    public User updateAvatar(String userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return userMapper.selectById(userId);
    }
}
