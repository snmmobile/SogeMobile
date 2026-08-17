package com.example.viewonlybrowser;

import java.net.URI;
import java.util.Locale;

/** Identifies the sensitive view fragment that must never reach the in-app page. */
final class ViewOnlyBlockPolicy {
    private static final String SOGEBANKING_REDIRECT_HOST = "www2.sogebanking.com";
    private static final String ACCOUNT_DETAILS_PATH =
            "/sogebanking/pages_personal/account_details.html";

    private ViewOnlyBlockPolicy() {
    }

    static boolean blocks(String url) {
        if (url == null) {
            return false;
        }

        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && SOGEBANKING_REDIRECT_HOST.equals(
                            uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT))
                    && ACCOUNT_DETAILS_PATH.equals(uri.getPath());
        } catch (Exception ignored) {
            return false;
        }
    }
}
