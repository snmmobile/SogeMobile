package com.example.viewonlybrowser;

final class AppUnlockSession {
    private static boolean unlocked;
    private static boolean authenticationInProgress;

    private AppUnlockSession() {}

    static synchronized boolean isUnlocked() {
        return unlocked;
    }

    static synchronized boolean isAuthenticationInProgress() {
        return authenticationInProgress;
    }

    static synchronized boolean beginAuthentication() {
        if (unlocked || authenticationInProgress) {
            return false;
        }
        authenticationInProgress = true;
        return true;
    }

    static synchronized void completeAuthentication(boolean succeeded) {
        authenticationInProgress = false;
        unlocked = succeeded;
    }

    static synchronized void lock() {
        unlocked = false;
        if (!authenticationInProgress) {
            authenticationInProgress = false;
        }
    }

    static synchronized void resetForTests() {
        unlocked = false;
        authenticationInProgress = false;
    }
}
