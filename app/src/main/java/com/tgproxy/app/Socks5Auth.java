package com.tgproxy.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Socks5Auth {

    public static String sha256Hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String hashPassword(String password) {
        if (password == null) return "";
        return sha256Hex(password.getBytes(StandardCharsets.UTF_8));
    }

    private Socks5Auth() {}
}
