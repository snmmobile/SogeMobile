package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ViewOnlyScriptsTest {
    @Test
    public void detectorUsesOnlyStableDashboardLabels() {
        String script = ViewOnlyScripts.targetDetector("viewonly://target-detected", true, true);

        assertTrue(script.contains("accounts.depositAccounts"));
        assertTrue(script.contains("comptes de dépôt"));
        assertTrue(script.contains("se déconnecter"));
        assertTrue(script.contains("newShareApplication.welcome"));
        assertTrue(script.contains("bienvenue"));
        assertFalse(script.contains("account_no_link"));
        assertFalse(script.contains("full_account_number"));
    }

    @Test
    public void detectorLocksBeforeItSignalsAndroid() {
        String script = ViewOnlyScripts.targetDetector("viewonly://target-detected", true, true);

        int lockCall = script.lastIndexOf("window.__installFunctionBlocker()");
        int signal = script.indexOf("window.location.href='viewonly://target-detected'");
        assertTrue(lockCall >= 0);
        assertTrue(signal > lockCall);
    }

    @Test
    public void blockerAllowsLoadAccountButPermanentlyReplacesLoadContent() {
        String script = ViewOnlyScripts.interactionBlocker(true, true);

        assertTrue(script.contains(".transfer_options,.account_details"));
        assertTrue(script.contains("display:none!important"));
        assertFalse(script.contains("a[href^=\"javascript:load_account("));
        assertFalse(script.contains("window.load_account=function"));
        assertTrue(script.contains("window.load_content=blockedLoadContent"));
        assertTrue(script.contains("Object.defineProperty(window,'load_content'"));
        assertTrue(script.contains("configurable:false"));
    }

    @Test
    public void protectionModesCanBeEnabledIndependently() {
        String readonlyOnly = ViewOnlyScripts.interactionBlocker(true, false);
        String functionsOnly = ViewOnlyScripts.interactionBlocker(false, true);
        String neither = ViewOnlyScripts.interactionBlocker(false, false);

        assertTrue(readonlyOnly.endsWith("window.__installReadOnlyBlocker();})();"));
        assertFalse(readonlyOnly.endsWith("window.__installFunctionBlocker();})();"));
        assertTrue(functionsOnly.endsWith("window.__installFunctionBlocker();})();"));
        assertFalse(functionsOnly.endsWith("window.__installReadOnlyBlocker();})();"));
        assertTrue(neither.endsWith(";})();"));
        assertFalse(neither.endsWith("window.__installReadOnlyBlocker();})();"));
        assertFalse(neither.endsWith("window.__installFunctionBlocker();})();"));
    }
}
