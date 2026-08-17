package com.example.viewonlybrowser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

final class ApkIntegrity {
    private ApkIntegrity() {
    }

    static String sha256(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            StringBuilder hexadecimal = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hexadecimal.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hexadecimal.toString();
        }
    }

    static boolean matchesSha256(File file, String expected) throws Exception {
        return expected != null && MessageDigest.isEqual(
                sha256(file).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expected.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
