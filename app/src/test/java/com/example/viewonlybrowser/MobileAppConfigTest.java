package com.example.viewonlybrowser;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MobileAppConfigTest {
    @Test
    public void detectsOptionalAndRequiredUpdatesSeparately() {
        int nextVersion = BuildConfig.VERSION_CODE + 1;
        MobileAppConfig optional = updateConfig(nextVersion, 1, false);
        MobileAppConfig required = updateConfig(nextVersion, nextVersion, true);

        assertTrue(optional.hasUpdate());
        assertFalse(optional.requiresUpdate());
        assertTrue(required.hasUpdate());
        assertTrue(required.requiresUpdate());
    }

    @Test
    public void ignoresIncompleteOrNonNewerReleases() {
        assertFalse(updateConfig(BuildConfig.VERSION_CODE, 1, false).hasUpdate());
        MobileAppConfig missingDigest = new MobileAppConfig(
                1, true, "", true, true,
                false, null, null, null, BuildConfig.START_URL,
                1, false, BuildConfig.VERSION_CODE + 1, "next",
                "https://github.com/snmmobile/SogeMobile/releases/download/v2/app.apk",
                null, null);
        assertFalse(missingDigest.hasUpdate());
    }

    @Test
    public void parsesOptionalDemoOverrideAndKeepsOlderPayloadsCompatible() throws Exception {
        JSONObject payload = basePayload();
        MobileAppConfig older = MobileAppConfig.parsePayload(payload);
        assertFalse(older.displayOverrideEnabled);

        payload.put("display_override", new JSONObject()
                .put("enabled", true)
                .put("account_salt", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .put("account_hash", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .put("balance_text", "GDES 1,234.56")
                .put("label", "TEMP / DEMO"));
        MobileAppConfig configured = MobileAppConfig.parsePayload(payload);
        assertTrue(configured.displayOverrideEnabled);
        assertTrue(configured.displayOverrideBalanceText.equals("GDES 1,234.56"));
    }

    @Test(expected = SecurityException.class)
    public void rejectsIncompleteEnabledDemoOverride() throws Exception {
        JSONObject payload = basePayload();
        payload.put("display_override", new JSONObject().put("enabled", true));
        MobileAppConfig.parsePayload(payload);
    }

    private static MobileAppConfig updateConfig(int latestVersion, int minimumVersion, boolean force) {
        return new MobileAppConfig(
                1, true, "", true, true,
                false, null, null, null, BuildConfig.START_URL,
                minimumVersion, force, latestVersion, "next",
                "https://github.com/snmmobile/SogeMobile/releases/download/v2/app.apk",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);
    }

    private static JSONObject basePayload() throws Exception {
        return new JSONObject()
                .put("schema_version", 1)
                .put("config_version", 1)
                .put("app", new JSONObject().put("enabled", true).put("maintenance_message", ""))
                .put("protections", new JSONObject()
                        .put("readonly_enabled", true)
                        .put("function_blocking_enabled", true))
                .put("web", new JSONObject().put("start_url", BuildConfig.START_URL))
                .put("update", new JSONObject()
                        .put("minimum_version_code", 1)
                        .put("force_update", false)
                        .put("version_code", JSONObject.NULL)
                        .put("version_name", JSONObject.NULL)
                        .put("download_url", JSONObject.NULL)
                        .put("sha256", JSONObject.NULL)
                        .put("release_notes", JSONObject.NULL));
    }
}
