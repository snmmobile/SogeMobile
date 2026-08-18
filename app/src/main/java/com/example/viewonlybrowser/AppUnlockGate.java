package com.example.viewonlybrowser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.View;

import java.util.concurrent.Executor;

final class AppUnlockGate {
    private static final int REQUEST_DEVICE_CREDENTIAL = 7104;

    private final Activity activity;
    private final View protectedContent;
    private final Runnable onUnlocked;
    private final KeyguardManager keyguardManager;
    private boolean awaitingDeviceCredential;

    AppUnlockGate(Activity activity, View protectedContent, Runnable onUnlocked) {
        this.activity = activity;
        this.protectedContent = protectedContent;
        this.onUnlocked = onUnlocked;
        this.keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
    }

    void requireUnlock() {
        if (AppUnlockSession.isUnlocked()) {
            revealContent();
            return;
        }

        protectedContent.setVisibility(View.INVISIBLE);
        if (awaitingDeviceCredential || !AppUnlockSession.beginAuthentication()) {
            return;
        }

        if (keyguardManager == null || !keyguardManager.isDeviceSecure()) {
            AppUnlockSession.completeAuthentication(false);
            showScreenLockRequired();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            showBiometricPrompt();
        } else {
            showDeviceCredentialPrompt();
        }
    }

    boolean handleActivityResult(int requestCode, int resultCode) {
        if (requestCode != REQUEST_DEVICE_CREDENTIAL) {
            return false;
        }
        awaitingDeviceCredential = false;
        boolean succeeded = resultCode == Activity.RESULT_OK;
        AppUnlockSession.completeAuthentication(succeeded);
        if (succeeded) {
            revealContent();
        } else {
            activity.finish();
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.P)
    private void showBiometricPrompt() {
        Executor executor = activity.getMainExecutor();
        android.hardware.biometrics.BiometricPrompt.Builder builder =
                new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                        .setTitle(activity.getString(R.string.unlock_app))
                        .setSubtitle(activity.getString(R.string.unlock_app_subtitle));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setDeviceCredentialAllowed(true);
        } else {
            builder.setNegativeButton(
                    activity.getString(R.string.use_screen_lock),
                    executor,
                    (dialog, which) -> showDeviceCredentialPrompt());
        }

        builder.build().authenticate(
                new CancellationSignal(),
                executor,
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        AppUnlockSession.completeAuthentication(true);
                        revealContent();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errorString) {
                        if (awaitingDeviceCredential) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P
                                && (errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                                || errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                                || errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT)) {
                            showDeviceCredentialPrompt();
                            return;
                        }
                        AppUnlockSession.completeAuthentication(false);
                        activity.finish();
                    }
                });
    }

    @SuppressWarnings("deprecation")
    private void showDeviceCredentialPrompt() {
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                activity.getString(R.string.unlock_app),
                activity.getString(R.string.unlock_app_subtitle));
        if (intent == null) {
            AppUnlockSession.completeAuthentication(false);
            showScreenLockRequired();
            return;
        }
        awaitingDeviceCredential = true;
        activity.startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL);
    }

    private void revealContent() {
        protectedContent.setVisibility(View.VISIBLE);
        onUnlocked.run();
    }

    private void showScreenLockRequired() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.screen_lock_required)
                .setMessage(R.string.screen_lock_required_message)
                .setPositiveButton(R.string.open_security_settings, (dialog, which) -> {
                    activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    activity.finish();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> activity.finish())
                .setOnCancelListener(dialog -> activity.finish())
                .show();
    }
}
