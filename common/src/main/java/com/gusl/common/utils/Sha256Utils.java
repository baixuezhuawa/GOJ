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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}