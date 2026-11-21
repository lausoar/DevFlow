package com.devflow.service.impl;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.service.UserService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.devflow.common.exception.BusinessException;
import com.devflow.util.PasswordPolicy;
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

        // 只写允许的字段，不能整实体回写：否则会把并发的企业移除/会话版本递增覆盖掉
        LambdaUpdateWrapper<User> patch = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (updates.getUsername() != null) {
            patch.set(User::getUsername, updates.getUsername());
        }
        if (updates.getRealName() != null) {
            // 空串而非 null：updateById 跳过 null，置 null 无法清空（聊天 sender_name 的 COALESCE 兼容空串）
            patch.set(User::getRealName, updates.getRealName().trim());
        }
        if (updates.getAvatar() != null) {
            patch.set(User::getAvatar, updates.getAvatar());
        }
        if (updates.getPhone() != null) {
            patch.set(User::getPhone, updates.getPhone());
        }

        userMapper.update(null, patch);
        return userMapper.selectById(userId);
    }

    /** 改密成功后的公共收尾：记录时间、清除强制改密标记 */
    private void afterPasswordChange(User user) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getPassword, user.getPassword())
                .set(User::getMustChangePassword, 0)
                .set(User::getPasswordUpdatedAt, java.time.LocalDateTime.now())
                .setSql("session_version = COALESCE(session_version, 0) + 1"));
    }

    @Override
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }

        // 强度校验：注册 / 改密 / 重置共用同一套规则
        java.util.List<String> problems = PasswordPolicy.validate(newPassword);
        if (!problems.isEmpty()) {
            throw new BusinessException(400, String.join("；", problems));
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        afterPasswordChange(user);
        log.info("用户 {} 修改密码成功", userId);
    }

    @Override
    public User updateAvatar(String userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatar, avatarUrl));
        return userMapper.selectById(userId);
    }
}
