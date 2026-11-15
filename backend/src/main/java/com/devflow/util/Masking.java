package com.devflow.util;

/**
 * 敏感字段脱敏。
 *
 * 原则：只有"确实需要看全"的角色给全量，其余一律脱敏；
 * 脱敏在服务端完成，不依赖前端隐藏 —— 前端隐藏挡不住直接调用接口。
 *
 * 与"字段级权限"相比这是更轻的做法：不引入字段 ACL 配置，
 * 而是按"谁能管理成员"这一现有权限点决定是否可见。
 */
public final class Masking {

    private Masking() {
    }

    /** 邮箱：保留首字符与域名，如 z***@example.com */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    /** 手机号：保留前三位与后四位，如 138****5678 */
    public static String phone(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String digits = value.trim();
        if (digits.length() < 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }
}
