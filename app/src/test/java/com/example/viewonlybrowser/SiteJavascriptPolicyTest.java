package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SiteJavascriptPolicyTest {
    @Test
    public void allowsOnlyTheSitesNumericLoadAccountCalls() {
        assertTrue(SiteJavascriptPolicy.allowsAccountLoad("javascript:load_account(1)"));
        assertTrue(SiteJavascriptPolicy.allowsAccountLoad("javascript:load_account(23);"));

        assertFalse(SiteJavascriptPolicy.allowsAccountLoad("javascript:load_content(1)"));
        assertFalse(SiteJavascriptPolicy.allowsAccountLoad("javascript:load_account(alert(1))"));
        assertFalse(SiteJavascriptPolicy.allowsAccountLoad("https://example.com"));
        assertFalse(SiteJavascriptPolicy.allowsAccountLoad(null));
    }
}
