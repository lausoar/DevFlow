package com.devflow.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private static final String SEEDED_PASSWORD_HASH =
            "$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale";

    private final UserMapper userMapper;

    @Value("${devflow.seed-demo-accounts:false}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        enableIfUnchanged("admin");
        enableIfUnchanged("zhangsan");
    }

    private void enableIfUnchanged(String username) {
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null || !SEEDED_PASSWORD_HASH.equals(user.getPassword())) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .eq(User::getPassword, SEEDED_PASSWORD_HASH)
                .set(User::getStatus, "ACTIVE")
                .set(User::getMustChangePassword, 0));
        log.warn("演示账号 {} 已按 DEVFLOW_SEED_DEMO_ACCOUNTS 配置启用；禁止在生产环境开启", username);
    }
}
