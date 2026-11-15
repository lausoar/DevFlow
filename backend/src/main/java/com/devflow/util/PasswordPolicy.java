package com.devflow.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 密码策略：统一的强度校验，注册 / 改密 / 重置三处共用。
 *
 * 规则刻意保守（8 位 + 三类字符）：再严格会逼用户写便利贴，
 * 比弱密码更危险；配合登录防爆破与双因素，这才是有效防线。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    /** 常见弱口令（小写比较），命中直接拒绝 */
    private static final Set<String> WEAK = Set.of(
            "12345678", "123456789", "password", "password1", "qwerty123", "admin123",
            "iloveyou", "abc12345", "11111111", "88888888", "devflow123", "test1234");

    private PasswordPolicy() {
    }

    /**
     * 校验密码，返回问题列表（空 = 通过）。
     * 返回全部问题而不是第一个，前端可以一次性提示全。
     */
    public static List<String> validate(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("密码不能为空");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("密码至少 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("密码不能超过 " + MAX_LENGTH + " 位");
        }
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        int kinds = (hasLower ? 1 : 0) + (hasUpper ? 1 : 0) + (hasDigit ? 1 : 0);
        if (kinds < 2) {
            problems.add("密码需包含字母和数字（或大小写混合）");
        }
        if (WEAK.contains(password.toLowerCase())) {
            problems.add("该密码过于常见，请更换");
        }
        // 连续重复字符（如 aaaaaaaa）或纯连续序列
        if (password.chars().distinct().count() == 1) {
            problems.add("密码不能是单一重复字符");
        }
        return problems;
    }

    public static boolean isValid(String password) {
        return validate(password).isEmpty();
    }
}
