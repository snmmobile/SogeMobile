package com.example.viewonlybrowser;

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
                1, true, "", true, true, BuildConfig.START_URL,
                1, false, BuildConfig.VERSION_CODE + 1, "next",
                "https://github.com/snmmobile/SogeMobile/releases/download/v2/app.apk",
                null, null);
        assertFalse(missingDigest.hasUpdate());
    }

    private static MobileAppConfig updateConfig(int latestVersion, int minimumVersion, boolean force) {
        return new MobileAppConfig(
                1, true, "", true, true, BuildConfig.START_URL,
                minimumVersion, force, latestVersion, "next",
                "https://github.com/snmmobile/SogeMobile/releases/download/v2/app.apk",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null);
    }
}
