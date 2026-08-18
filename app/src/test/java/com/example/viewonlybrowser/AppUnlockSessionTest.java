package com.example.viewonlybrowser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AppUnlockSessionTest {
    @Before
    @After
    public void reset() {
        AppUnlockSession.resetForTests();
    }

    @Test
    public void successfulAuthenticationUnlocksSession() {
        assertTrue(AppUnlockSession.beginAuthentication());
        AppUnlockSession.completeAuthentication(true);

        assertTrue(AppUnlockSession.isUnlocked());
        assertFalse(AppUnlockSession.isAuthenticationInProgress());
    }

    @Test
    public void onlyOneAuthenticationCanRunAtOnce() {
        assertTrue(AppUnlockSession.beginAuthentication());
        assertFalse(AppUnlockSession.beginAuthentication());
        assertTrue(AppUnlockSession.isAuthenticationInProgress());
    }

    @Test
    public void lockingRequiresAuthenticationAgain() {
        assertTrue(AppUnlockSession.beginAuthentication());
        AppUnlockSession.completeAuthentication(true);
        AppUnlockSession.lock();

        assertFalse(AppUnlockSession.isUnlocked());
        assertTrue(AppUnlockSession.beginAuthentication());
    }

    @Test
    public void failedAuthenticationKeepsSessionLocked() {
        assertTrue(AppUnlockSession.beginAuthentication());
        AppUnlockSession.completeAuthentication(false);

        assertFalse(AppUnlockSession.isUnlocked());
        assertFalse(AppUnlockSession.isAuthenticationInProgress());
    }
}
