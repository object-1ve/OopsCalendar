package com.oops.calendar.service.news;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具:财联社接口签名需要 sign = MD5(SHA1(排序后的查询串))。
 */
final class DigestUtil {

    private DigestUtil() {
    }

    static String sha1Hex(String input) {
        return hex(digest("SHA-1", input));
    }

    static String md5Hex(String input) {
        return hex(digest("MD5", input));
    }

    private static byte[] digest(String algorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("不支持的摘要算法: " + algorithm, e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
