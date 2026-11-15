package com.devflow.util;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * TOTP（RFC 6238）实现：生成密钥、计算动态码、校验。
 *
 * 只依赖 JDK 的 HmacSHA1 与 commons-codec 的 Base32，不引入完整 OTP 框架。
 * 校验窗口为 ±1 个时间步（默认 30s），容忍客户端时钟偏差，但不放宽到更大范围
 * —— 窗口越大，被暴力猜中的概率越高。
 */
public final class TotpUtil {

    /** 时间步长（秒） */
    private static final int TIME_STEP_SECONDS = 30;
    /** 动态码位数 */
    private static final int DIGITS = 6;
    /** 允许的时间步偏差 */
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20; // 160 bit，RFC 4226 推荐

    private static final Base32 BASE32 = new Base32();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** 生成新的 Base32 密钥（不带填充，便于用户手工输入） */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return BASE32.encodeToString(bytes).replace("=", "");
    }

    /** 当前时间步对应的动态码 */
    public static String currentCode(String base32Secret) {
        return generateCode(base32Secret, System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS);
    }

    /**
     * 校验动态码（允许 ±1 个时间步）。
     * 用常量时间比较，避免按位猜测的时间侧信道。
     */
    public static boolean verify(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String normalized = code.trim().replace(" ", "");
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        long counter = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            String expected = generateCode(base32Secret, counter + offset);
            if (java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** 生成 otpauth:// 链接，前端可渲染成二维码给用户扫码 */
    public static String otpauthUrl(String issuer, String account, String base32Secret) {
        String label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    private static String generateCode(String base32Secret, long counter) {
        byte[] key = BASE32.decode(base32Secret);
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
