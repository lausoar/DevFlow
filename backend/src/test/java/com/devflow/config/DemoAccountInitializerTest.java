package com.devflow.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemoAccountInitializer 演示账号保护")
class DemoAccountInitializerTest {

    private static final String SEEDED_HASH =
            "$2a$10$1ByLIQKjzkDIWsVbyyLkW.aOzv3Nmfj3VcaWmoxNMuL.tUY2Egale";

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private DemoAccountInitializer initializer;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), User.class);
    }

    @Test
    @DisplayName("默认禁用时完全无操作")
    void disabledInitializerIsNoOp() {
        ReflectionTestUtils.setField(initializer, "enabled", false);

        initializer.run(null);

        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("启用后仍跳过不存在或密码已修改的账号")
    void missingOrModifiedAccountsAreNotReactivated() {
        ReflectionTestUtils.setField(initializer, "enabled", true);
        when(userMapper.selectOne(any())).thenReturn(null, demoUser("u2", "changed-hash"));

        initializer.run(null);

        verify(userMapper, times(2)).selectOne(any());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("仅精确已知哈希匹配，并以 ID+哈希条件原子更新")
    void exactKnownHashIsConditionallyReactivated() {
        ReflectionTestUtils.setField(initializer, "enabled", true);
        when(userMapper.selectOne(any())).thenReturn(
                demoUser("admin-id", SEEDED_HASH), demoUser("zhangsan-id", "changed-hash"));

        initializer.run(null);

        ArgumentCaptor<LambdaUpdateWrapper<User>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<User> update = captor.getValue();
        // WHERE 参数在生成 SQL 片段时才写入 paramNameValuePairs。
        update.getSqlSegment();
        assertThat(update.getParamNameValuePairs().values())
                .contains("admin-id", SEEDED_HASH, "ACTIVE", 0);
        assertThat(update.getSqlSet()).contains("status", "must_change_password");
    }

    private static User demoUser(String id, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setPassword(passwordHash);
        user.setStatus("INACTIVE");
        user.setMustChangePassword(1);
        return user;
    }
}
