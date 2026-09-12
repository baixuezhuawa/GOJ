package com.gusl.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256工具类
 */
public final class Sha256Utils {

    private Sha256Utils() {

    }

    /**
     * 计算字符串 UTF-8 字节内容的 SHA-256。
     *
     * @param text 待计算的字符串
     * @return 64 位十六进制 SHA-256 摘要
     */
    public static String sha256Hex(String text) {
        MessageDigest digest = createSha256Digest();
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    /**
     * @return SHA-256 摘要计算器。
     */
    public static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            // Java 标准环境必须支持 SHA-256，出现该异常说明运行环境异常。
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}