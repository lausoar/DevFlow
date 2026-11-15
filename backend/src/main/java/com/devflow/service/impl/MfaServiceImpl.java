package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.service.MfaService;
import com.devflow.util.TotpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双因素认证实现。
 *
 * 挑战凭证放在进程内 Map（5 分钟 TTL，单次使用后立即失效）。
 * 多实例部署时需要迁到 Redis —— 这里与登录防爆破计数是同一类单机假设，
 * 在文档「已知限制」里一并记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final UserMapper userMapper;

    /** 挑战凭证 → (userId, 过期时间) */
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    private static final long CHALLENGE_TTL_MS = 5 * 60 * 1000L;
    private static final String ISSUER = "DevFlow";

    private record Challenge(String userId, long expiresAt) {
    }

    @Override
    public BindResult beginBind(String userId) {
        User user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getTotpEnabled())) {
            throw new BusinessException(400, "已启用双因素认证，如需更换请先关闭");
        }
        String secret = TotpUtil.generateSecret();
        String url = TotpUtil.otpauthUrl(ISSUER, user.getUsername(), secret);
        return new BindResult(secret, url);
    }

    @Override
    public void confirmBind(String userId, String secret, String code) {
        User user = requireUser(userId);
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(400, "缺少密钥，请重新发起绑定");
        }
        // 必须真的校验一次动态码：否则用户可能绑定了一个自己都扫不出来的密钥，
        // 下次登录就永久进不去了
        if (!TotpUtil.verify(secret, code)) {
            throw new BusinessException(400, "动态码不正确，请确认验证器时间是否准确后重试");
        }
        User patch = new User();
        patch.setId(userId);
        patch.setTotpSecret(secret);
        patch.setTotpEnabled(1);
        patch.setTotpBoundAt(LocalDateTime.now());
        userMapper.updateById(patch);
        log.info("用户 {} 已启用双因素认证", userId);
    }

    @Override
    public void disable(String userId, String code) {
        User user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getTotpEnabled())) {
            throw new BusinessException(400, "尚未启用双因素认证");
        }
        if (!TotpUtil.verify(user.getTotpSecret(), code)) {
            throw new BusinessException(400, "动态码不正确");
        }
        // 注意：MyBatis Plus updateById 会跳过 null 字段，清空密钥需要显式 UPDATE
        User patch = new User();
        patch.setId(userId);
        patch.setTotpEnabled(0);
        userMapper.updateById(patch);
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTotpSecret, null)
                .set(User::getTotpBoundAt, null));
        log.info("用户 {} 已关闭双因素认证", userId);
    }

    @Override
    public boolean isEnabled(String userId) {
        User user = userMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getTotpEnabled());
    }

    @Override
    public String issueChallenge(String userId) {
        evictExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        challenges.put(token, new Challenge(userId, System.currentTimeMillis() + CHALLENGE_TTL_MS));
        return token;
    }

    @Override
    public void verifyChallenge(String challengeToken, String expectedUserId, String code) {
        if (!StringUtils.hasText(challengeToken)) {
            throw new BusinessException(401, "缺少双因素凭证，请重新登录");
        }
        Challenge challenge = challenges.get(challengeToken);
        if (challenge == null || challenge.expiresAt() < System.currentTimeMillis()) {
            challenges.remove(challengeToken);
            throw new BusinessException(401, "双因素凭证已失效，请重新登录");
        }
        // 凭证绑定用户：防止把一个自己的凭证拿去完成别人的登录
        if (expectedUserId != null && !expectedUserId.equals(challenge.userId())) {
            throw new BusinessException(401, "双因素凭证与账号不匹配");
        }
        User user = requireUser(challenge.userId());
        if (!TotpUtil.verify(user.getTotpSecret(), code)) {
            // 校验失败也消耗凭证：否则可以在 5 分钟内无限次猜 6 位码
            challenges.remove(challengeToken);
            throw new BusinessException(401, "动态码不正确");
        }
        // 成功即消耗，同一凭证不能复用
        challenges.remove(challengeToken);
    }

    private User requireUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /** 清理过期挑战（容量保护，不做全量清空） */
    private void evictExpired() {
        if (challenges.size() < 1000) {
            return;
        }
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
