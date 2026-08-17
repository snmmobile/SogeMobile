package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReleaseDownloadPolicyTest {
    @Test
    public void acceptsOnlyOfficialDirectGithubApkAssets() {
        assertTrue(ReleaseDownloadPolicy.isTrusted(
                "https://github.com/snmmobile/SogeMobile/releases/download/v1.1.0/SogeMobile-v1.1.apk"));
        assertFalse(ReleaseDownloadPolicy.isTrusted(
                "https://github.com/snmmobile/SogeMobile/releases/tag/v1.1.0"));
        assertFalse(ReleaseDownloadPolicy.isTrusted(
                "https://github.com/attacker/SogeMobile/releases/download/v1.1.0/app.apk"));
        assertFalse(ReleaseDownloadPolicy.isTrusted(
                "https://github.com.evil.test/snmmobile/SogeMobile/releases/download/v1/app.apk"));
    }
}
