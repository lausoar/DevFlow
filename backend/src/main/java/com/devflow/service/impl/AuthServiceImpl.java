package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.dto.request.LoginRequest;
import com.devflow.dto.request.RegisterRequest;
import com.devflow.dto.response.AuthResponse;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuthService;
import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public AuthResponse login(LoginRequest request) {
        // 登录防爆破：按账号限流，15 分钟内失败 5 次即临时锁定（无论密码对错）。
        // trim 后再小写：否则 " admin" 与 "admin" 是两个 key，加空格即可绕过锁定
        String bruteKey = request.getUsername() == null ? "" : request.getUsername().trim().toLowerCase();
        checkLoginLock(bruteKey);

        // 查询用户：账号含 @ 按邮箱匹配，否则按用户名匹配（支持两种登录方式）
        String account = request.getUsername() == null ? "" : request.getUsername().trim();
        User user = account.contains("@")
            ? userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, account))
            : userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, account));

        if (user == null) {
            recordFailedLogin(bruteKey);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedLogin(bruteKey);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 登录成功，清除失败记录
        failedLogins.remove(bruteKey);

        // 检查状态
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 生成 Token
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 清除密码字段返回
        user.setPassword(null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 检查邮箱是否已注册
        Long emailCount = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            throw new BusinessException(400, "邮箱已被注册");
        }

        // 注册只创建普通用户；企业通过邀请码加入，或登录后在企业页自行创建
        String enterpriseId = null;

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setEnterpriseId(null);
        user.setDeleted(0);
        userMapper.insert(user);

        // 生成 Token
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        user.setPassword(null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh Token 已过期");
        }

        String userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        user.setPassword(null);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(user)
                .build();
    }

    @Override
    public User getCurrentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 将 Access Token 加入黑名单，TTL 为剩余有效期
        if (accessToken != null && jwtUtil.validateToken(accessToken)) {
            long remaining = jwtUtil.getRemainingExpiration(accessToken);
            tokenBlacklistService.blacklist(accessToken, remaining);
        }

        // 将 Refresh Token 也加入黑名单
        if (refreshToken != null && jwtUtil.validateToken(refreshToken)) {
            long remaining = jwtUtil.getRemainingExpiration(refreshToken);
            tokenBlacklistService.blacklist(refreshToken, remaining);
        }
    }

    // ============================================
    // 登录防爆破
    // ============================================

    /** 15 分钟窗口内最大失败次数 */
    private static final int MAX_LOGIN_FAILED = 5;
    private static final long LOGIN_WINDOW_MS = 15 * 60_000L;
    /** 追踪账号数上限（超出时按过期时间淘汰，不做全量清空） */
    private static final int MAX_TRACKED_ACCOUNTS = 10_000;
    /** key = 用户名（小写）→ 窗口内失败时间戳队列 */
    private final Map<String, Deque<Long>> failedLogins = new ConcurrentHashMap<>();

    private void checkLoginLock(String key) {
        Deque<Long> queue = failedLogins.get(key);
        if (queue == null) {
            return;
        }
        synchronized (queue) {
            long now = System.currentTimeMillis();
            while (!queue.isEmpty() && now - queue.peekFirst() > LOGIN_WINDOW_MS) {
                queue.pollFirst();
            }
            if (queue.size() >= MAX_LOGIN_FAILED) {
                throw new BusinessException(429, "失败次数过多，账号已临时锁定，请 15 分钟后再试");
            }
        }
    }

    private void recordFailedLogin(String key) {
        Deque<Long> queue = failedLogins.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(System.currentTimeMillis());
        }
        evictExpiredEntries();
    }

    /**
     * 容量保护：只淘汰"窗口已过期"的条目，绝不 clear()。
     * 原实现的 clear() 会让攻击者用一万个随机用户名把所有人的锁定状态一并清空。
     */
    private void evictExpiredEntries() {
        if (failedLogins.size() <= MAX_TRACKED_ACCOUNTS) {
            return;
        }
        long now = System.currentTimeMillis();
        failedLogins.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() > LOGIN_WINDOW_MS) {
                    q.pollFirst();
                }
                return q.isEmpty();
            }
        });
        // 仍超限：说明确有大量账号在窗口内反复失败，按最旧记录淘汰，保留锁定语义
        if (failedLogins.size() > MAX_TRACKED_ACCOUNTS) {
            failedLogins.entrySet().stream()
                    .sorted(java.util.Comparator.comparingLong(e -> {
                        Deque<Long> q = e.getValue();
                        synchronized (q) {
                            return q.isEmpty() ? Long.MAX_VALUE : q.peekFirst();
                        }
                    }))
                    .limit(failedLogins.size() - MAX_TRACKED_ACCOUNTS)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(failedLogins::remove);
        }
    }
}