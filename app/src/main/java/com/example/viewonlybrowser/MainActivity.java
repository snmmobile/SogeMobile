package com.example.viewonlybrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

public final class MainActivity extends Activity {
    private MobileControlClient controlClient;
    private UpdateInstaller updateInstaller;
    private Button openWebsiteButton;
    private TextView availabilityMessage;
    private MobileAppConfig config;
    private AlertDialog downloadDialog;
    private AlertDialog updateDialog;
    private AppUnlockGate unlockGate;
    private boolean initialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        View protectedContent = findViewById(R.id.mainRoot);
        protectedContent.setVisibility(View.INVISIBLE);
        unlockGate = new AppUnlockGate(this, protectedContent, this::onUnlocked);
    }

    @Override
    protected void onStart() {
        super.onStart();
        unlockGate.requireUnlock();
    }

    private void onUnlocked() {
        if (!initialized) {
            initialized = true;
            initializeApp();
        }
        resumePendingInstall();
    }

    private void initializeApp() {

        openWebsiteButton = findViewById(R.id.openWebsiteButton);
        Button openStaticTestButton = findViewById(R.id.openStaticTestButton);
        availabilityMessage = findViewById(R.id.availabilityMessage);
        controlClient = new MobileControlClient(this);
        updateInstaller = new UpdateInstaller(this);
        config = controlClient.cachedOrSafeConfig();
        applyConfig(config);

        openWebsiteButton.setOnClickListener(view -> {
            if (config.appEnabled && !(config.forceUpdate && config.requiresUpdate())) {
                startActivity(new Intent(this, BrowserActivity.class));
            }
        });

        if (BuildConfig.DEBUG) {
            openStaticTestButton.setVisibility(View.VISIBLE);
            openStaticTestButton.setOnClickListener(view -> startActivity(
                    new Intent(this, BrowserActivity.class)
                            .putExtra(BrowserActivity.EXTRA_STATIC_DASHBOARD_TEST, true)));
        }

        controlClient.fetchConfig((loaded, fresh) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            config = loaded;
            applyConfig(config);
            controlClient.sendEvent("app_open", config);
            if (config.hasUpdate()) {
                showUpdateDialog(config);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppUnlockSession.isUnlocked()) {
            resumePendingInstall();
        }
    }

    private void resumePendingInstall() {
        if (updateInstaller != null && config != null) {
            try {
                updateInstaller.resumePendingInstall(this, config);
            } catch (IllegalStateException exception) {
                showUpdateError(config);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (unlockGate != null && unlockGate.handleActivityResult(requestCode, resultCode)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if (controlClient != null) {
            controlClient.close();
        }
        if (updateInstaller != null) {
            updateInstaller.close();
        }
        if (downloadDialog != null) {
            downloadDialog.dismiss();
        }
        if (updateDialog != null) {
            updateDialog.dismiss();
        }
        super.onDestroy();
    }

    private void applyConfig(MobileAppConfig current) {
        boolean blockedByUpdate = current.forceUpdate && current.requiresUpdate();
        openWebsiteButton.setEnabled(current.appEnabled && !blockedByUpdate);
        if (!current.appEnabled) {
            availabilityMessage.setText(current.maintenanceMessage.isEmpty()
                    ? getString(R.string.app_temporarily_unavailable)
                    : current.maintenanceMessage);
        } else if (blockedByUpdate) {
            availabilityMessage.setText(R.string.update_required);
        } else {
            availabilityMessage.setText("");
        }
    }

    private void showUpdateDialog(MobileAppConfig current) {
        controlClient.sendEvent("update_prompted", current);
        if (updateDialog != null && updateDialog.isShowing()) {
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_update, null);
        TextView title = content.findViewById(R.id.updateTitle);
        TextView version = content.findViewById(R.id.updateVersion);
        TextView message = content.findViewById(R.id.updateMessage);
        Button updateNow = content.findViewById(R.id.updateNowButton);
        Button later = content.findViewById(R.id.updateLaterButton);

        title.setText(current.forceUpdate ? R.string.update_required : R.string.update_available);
        message.setText(current.forceUpdate
                ? R.string.update_required_message
                : R.string.update_available_message);
        if (current.latestVersionName == null || current.latestVersionName.trim().isEmpty()) {
            version.setVisibility(View.GONE);
        } else {
            version.setText(getString(R.string.version_label, current.latestVersionName));
        }
        later.setVisibility(current.forceUpdate ? View.GONE : View.VISIBLE);

        updateDialog = new AlertDialog.Builder(this).setView(content).create();
        updateDialog.setCancelable(!current.forceUpdate);
        updateDialog.setCanceledOnTouchOutside(!current.forceUpdate);
        updateNow.setOnClickListener(view -> {
            updateDialog.dismiss();
            updateDialog = null;
            startUpdateDownload(current);
        });
        later.setOnClickListener(view -> {
            updateDialog.dismiss();
            updateDialog = null;
        });
        updateDialog.setOnDismissListener(dialog -> updateDialog = null);
        updateDialog.show();

        Window window = updateDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = Math.min(screenWidth - dp(40), dp(420));
            window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startUpdateDownload(MobileAppConfig current) {
        downloadDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.downloading_update)
                .setMessage(getString(R.string.download_progress, 0))
                .setCancelable(false)
                .create();
        downloadDialog.show();

        updateInstaller.download(current, new UpdateInstaller.Callback() {
            @Override
            public void onProgress(int percent) {
                if (!isFinishing() && !isDestroyed() && downloadDialog != null) {
                    downloadDialog.setMessage(getString(R.string.download_progress, percent));
                }
            }

            @Override
            public void onReady(File apk) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                dismissDownloadDialog();
                try {
                    updateInstaller.installOrRequestPermission(MainActivity.this, apk, current);
                } catch (IllegalStateException exception) {
                    showUpdateError(current);
                }
            }

            @Override
            public void onError() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                dismissDownloadDialog();
                showUpdateError(current);
            }
        });
    }

    private void showUpdateError(MobileAppConfig current) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.update_failed)
                .setMessage(R.string.update_failed_message)
                .setPositiveButton(R.string.retry, (dialog, which) -> startUpdateDownload(current));
        if (!current.requiresUpdate()) {
            builder.setNegativeButton(R.string.later, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCancelable(!current.requiresUpdate());
        dialog.setCanceledOnTouchOutside(!current.requiresUpdate());
        dialog.show();
    }

    private void dismissDownloadDialog() {
        if (downloadDialog != null) {
            downloadDialog.dismiss();
            downloadDialog = null;
        }
    }
}
