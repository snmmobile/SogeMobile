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
    public void blockerAllowsLoadAccountAndLoadContent() {
        String script = ViewOnlyScripts.interactionBlocker(true, true);

        assertTrue(script.contains(".transfer_options,.account_details"));
        assertTrue(script.contains("display:none!important"));
        assertFalse(script.contains("a[href^=\"javascript:load_account("));
        assertFalse(script.contains("window.load_account=function"));
        assertFalse(script.contains("a[href^=\"javascript:load_content("));
        assertFalse(script.contains("window.load_content="));
        assertFalse(script.contains("Object.defineProperty(window,'load_content'"));
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

    @Test
    public void temporaryDisplayOverrideTargetsOnlyConfiguredAccountAndMarksIt() {
        String script = ViewOnlyScripts.temporaryAccountDisplayOverride("1234567890", "GDES 1,234.56");

        assertTrue(script.contains("tr.account_item[account_id=\"'+accountId+'\"]"));
        assertTrue(script.contains("GDES 1,234.56"));
        assertTrue(script.contains("sogemobile-temp-balance"));
        assertTrue(script.contains("Temporary local display override"));
        assertFalse(script.contains("account_no_link"));
    }

    @Test
    public void temporaryDisplayOverrideRejectsInvalidIdentifiers() {
        assertTrue(ViewOnlyScripts.temporaryAccountDisplayOverride("not-an-account", "GDES 1.00").isEmpty());
        assertTrue(ViewOnlyScripts.temporaryAccountDisplayOverride("123456", " ").isEmpty());
    }
}
