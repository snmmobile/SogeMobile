package com.example.viewonlybrowser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Synthetic, debug-only dashboard configuration. It contains no customer or bank data. */
final class StaticDashboardFixture {
    static final String ASSET_NAME = "static_dashboard.html";
    static final String BASE_URL = "https://fixture.sogemobile.invalid/dashboard/";
    static final String TARGET_ACCOUNT_ID = "9000000256";
    private static final String HOST = "fixture.sogemobile.invalid";
    private static final String SALT = "11111111111111111111111111111111";

    private StaticDashboardFixture() {
    }

    static MobileAppConfig config() {
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("The static dashboard is available only in debug builds");
        }
        return new MobileAppConfig(
                Integer.MAX_VALUE, true, "", true, true,
                true, SALT, sha256(SALT + ":" + TARGET_ACCOUNT_ID), "GDES 26,795.45",
                BuildConfig.START_URL, 1, false, null, null, null, null,
                "Static dashboard test");
    }

    static boolean blocks(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && HOST.equals(uri.getHost() == null
                    ? "" : uri.getHost().toLowerCase(Locale.ROOT))
                    && ("/pages_personal/account_details.html".equals(path)
                    || "/pages_personal/transfers_landing.html".equals(path));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
