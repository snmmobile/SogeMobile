package com.example.viewonlybrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StaticDashboardFixtureTest {
    @Test
    public void fixtureBlocksOnlyItsSyntheticSensitivePages() {
        assertTrue(StaticDashboardFixture.blocks(
                "https://fixture.sogemobile.invalid/pages_personal/account_details.html?row=2"));
        assertTrue(StaticDashboardFixture.blocks(
                "https://fixture.sogemobile.invalid/pages_personal/transfers_landing.html"));
        assertFalse(StaticDashboardFixture.blocks(
                "https://fixture.sogemobile.invalid/pages_personal/accounts.html"));
        assertFalse(StaticDashboardFixture.blocks(
                "https://fixture.sogemobile.invalid.evil.test/pages_personal/account_details.html"));
    }

    @Test
    public void fixtureConfigEnablesEveryProtectionWithSyntheticData() {
        MobileAppConfig config = StaticDashboardFixture.config();

        assertTrue(config.readonlyEnabled);
        assertTrue(config.functionBlockingEnabled);
        assertTrue(config.displayOverrideEnabled);
        assertTrue(config.displayOverrideAccountHash.matches("[a-f0-9]{64}"));
        assertFalse(config.displayOverrideAccountHash.contains(
                StaticDashboardFixture.TARGET_ACCOUNT_ID));
    }
}
