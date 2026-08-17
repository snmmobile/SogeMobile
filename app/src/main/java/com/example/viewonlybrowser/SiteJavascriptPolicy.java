package com.example.viewonlybrowser;

/** Narrow pass-through for JavaScript links owned and executed by the trusted site. */
final class SiteJavascriptPolicy {
    private SiteJavascriptPolicy() {
    }

    static boolean allowsAccountLoad(String url) {
        return url != null && url.matches("(?i)^javascript:load_account\\([0-9]+\\);?$");
    }
}
