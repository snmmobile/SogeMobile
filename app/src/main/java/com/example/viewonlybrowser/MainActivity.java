package com.example.viewonlybrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openWebsiteButton = findViewById(R.id.openWebsiteButton);
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
        if (updateInstaller != null && config != null) {
            try {
                updateInstaller.resumePendingInstall(this, config);
            } catch (IllegalStateException exception) {
                showUpdateError(config);
            }
        }
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
        String title = current.forceUpdate
                ? getString(R.string.update_required)
                : getString(R.string.update_available);
        StringBuilder message = new StringBuilder();
        if (current.latestVersionName != null) {
            message.append(getString(R.string.version_label, current.latestVersionName));
        }
        if (current.releaseNotes != null) {
            if (message.length() > 0) message.append("\n\n");
            message.append(current.releaseNotes);
        }
        if (current.sha256 != null) {
            if (message.length() > 0) message.append("\n\n");
            message.append(getString(R.string.sha256_label, current.sha256));
        }

        controlClient.sendEvent("update_prompted", current);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.download_update,
                        (dialog, which) -> startUpdateDownload(current));
        if (!current.forceUpdate) {
            builder.setNegativeButton(R.string.later, null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!current.forceUpdate);
        dialog.setCancelable(!current.forceUpdate);
        dialog.show();
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
