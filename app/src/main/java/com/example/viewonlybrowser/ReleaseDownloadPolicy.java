package com.example.viewonlybrowser;

import java.net.URI;
import java.util.Locale;

/** Accepts only direct APK assets published by the official public GitHub repository. */
final class ReleaseDownloadPolicy {
    private ReleaseDownloadPolicy() {
    }

    static boolean isTrusted(String candidate) {
        try {
            URI uri = new URI(candidate);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "github.com".equals(host)
                    && path.startsWith("/snmmobile/SogeMobile/releases/download/")
                    && path.toLowerCase(Locale.ROOT).endsWith(".apk")
                    && uri.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
