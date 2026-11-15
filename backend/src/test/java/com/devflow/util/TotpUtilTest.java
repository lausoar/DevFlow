package com.devflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP 单测（RFC 6238 测试向量）。
 *
 * 用 RFC 附录 B 的已知密钥/时间/期望码验证实现正确 —— 这是"能不能用真实验证器
 * App 扫出来"的唯一可靠证据，光靠自测闭环（自己生成自己验）会一起错。
 */
@DisplayName("TotpUtil TOTP 实现")
class TotpUtilTest {

    /** RFC 6238 附录 B 的 SHA1 测试密钥（ASCII "12345678901234567890" 的 Base32） */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    @DisplayName("校验 RFC 测试向量：59 秒时动态码为 287082")
    void matchesRfcVector() {
        // RFC 6238 附录 B：T=59s, TOTP(SHA1) = 94287082（8 位），取后 6 位 = 287082
        String code = codeAt(RFC_SECRET, 59L);
        assertThat(code).isEqualTo("287082");
    }

    @Test
    @DisplayName("生成与校验闭环：当前时间步应通过")
    void verifyOwnCode() {
        String secret = TotpUtil.generateSecret();
        String code = TotpUtil.currentCode(secret);
        assertThat(TotpUtil.verify(secret, code)).isTrue();
    }

    @Test
    @DisplayName("错误的动态码被拒绝")
    void rejectsWrongCode() {
        String secret = TotpUtil.generateSecret();
        String code = TotpUtil.currentCode(secret);
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertThat(TotpUtil.verify(secret, wrong)).isFalse();
    }

    @Test
    @DisplayName("非法输入（空、非 6 位、非数字）一律拒绝，不抛异常")
    void rejectsMalformedInput() {
        String secret = TotpUtil.generateSecret();
        assertThat(TotpUtil.verify(secret, null)).isFalse();
        assertThat(TotpUtil.verify(secret, "")).isFalse();
        assertThat(TotpUtil.verify(secret, "12345")).isFalse();
        assertThat(TotpUtil.verify(secret, "abcdef")).isFalse();
        assertThat(TotpUtil.verify(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("生成的密钥是 Base32 且长度合理")
    void generatedSecretLooksLikeBase32() {
        String secret = TotpUtil.generateSecret();
        assertThat(secret).matches("[A-Z2-7]+");
        // 20 字节 = 160 bit → Base32 约 32 字符
        assertThat(secret.length()).isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("otpauth 链接带 issuer/secret/algorithm，可被验证器识别")
    void otpauthUrl() {
        String url = TotpUtil.otpauthUrl("DevFlow", "alice", "ABCDEFGH");
        assertThat(url).startsWith("otpauth://totp/");
        assertThat(url).contains("secret=ABCDEFGH");
        assertThat(url).contains("issuer=DevFlow");
        assertThat(url).contains("digits=6");
    }

    /** 用反射调用私有 generateCode（指定时间步），避免测试里等待真实时间流逝 */
    @SuppressWarnings("unchecked")
    private String codeAt(String secret, long epochSeconds) {
        try {
            var method = TotpUtil.class.getDeclaredMethod("generateCode", String.class, long.class);
            method.setAccessible(true);
            return (String) method.invoke(null, secret, epochSeconds / 30);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
