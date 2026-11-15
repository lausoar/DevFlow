package com.devflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordPolicy 密码策略")
class PasswordPolicyTest {

    @Test
    @DisplayName("合格密码通过")
    void acceptsStrongPassword() {
        assertThat(PasswordPolicy.isValid("Devflow2026")).isTrue();
        assertThat(PasswordPolicy.isValid("aB3defgh")).isTrue();
    }

    @Test
    @DisplayName("过短被拒绝")
    void rejectsShort() {
        assertThat(PasswordPolicy.validate("Ab1")).anyMatch(m -> m.contains("至少"));
        assertThat(PasswordPolicy.isValid("Ab1")).isFalse();
    }

    @Test
    @DisplayName("单一字符类型被拒绝")
    void rejectsSingleCharClass() {
        // 全小写
        assertThat(PasswordPolicy.validate("abcdefghij")).anyMatch(m -> m.contains("字母和数字"));
        // 全数字
        assertThat(PasswordPolicy.validate("1234567890")).anyMatch(m -> m.contains("字母和数字"));
    }

    @Test
    @DisplayName("常见弱口令被拒绝")
    void rejectsCommonWeak() {
        assertThat(PasswordPolicy.validate("password1")).anyMatch(m -> m.contains("过于常见"));
        assertThat(PasswordPolicy.validate("devflow123")).anyMatch(m -> m.contains("过于常见"));
    }

    @Test
    @DisplayName("单一重复字符被拒绝")
    void rejectsRepeated() {
        // 全部是同一个字符（字符种类数为 1）
        assertThat(PasswordPolicy.validate("aaaaaaaa")).anyMatch(m -> m.contains("单一重复"));
    }

    @Test
    @DisplayName("空密码返回单条明确错误")
    void rejectsEmpty() {
        assertThat(PasswordPolicy.validate("")).containsExactly("密码不能为空");
        assertThat(PasswordPolicy.validate(null)).containsExactly("密码不能为空");
    }

    @Test
    @DisplayName("一次返回全部问题，而不是只报第一条")
    void reportsAllProblems() {
        // 过短 + 只有一种字符类型 → 两个问题一起返回
        var problems = PasswordPolicy.validate("abcdef");
        assertThat(problems.size()).isGreaterThanOrEqualTo(2);
    }
}
