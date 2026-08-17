package com.example.viewonlybrowser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads a signed-config release into private cache, verifies it, then hands it to Android. */
final class UpdateInstaller {
    interface Callback {
        void onProgress(int percent);

        void onReady(File apk);

        void onError();
    }

    private static final long MAX_APK_BYTES = 150L * 1024L * 1024L;
    private static final String PREFERENCES = "mobile_update";
    private static final String PENDING_APK = "pending_apk";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    UpdateInstaller(Context context) {
        this.context = context.getApplicationContext();
    }

    void download(MobileAppConfig config, Callback callback) {
        executor.execute(() -> {
            File partial = null;
            try {
                if (!config.hasUpdate() || !ReleaseDownloadPolicy.isTrusted(config.downloadUrl)) {
                    throw new SecurityException("Incomplete or untrusted update metadata");
                }

                File updateDirectory = new File(context.getCacheDir(), "updates");
                if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                    throw new IllegalStateException("Cannot create update cache");
                }
                File target = new File(updateDirectory, "sogemobile-v" + config.latestVersionCode + ".apk");
                partial = new File(updateDirectory, target.getName() + ".part");
                if (partial.exists() && !partial.delete()) {
                    throw new IllegalStateException("Cannot replace partial update");
                }

                HttpURLConnection connection = (HttpURLConnection) new URL(config.downloadUrl).openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK || !isTrustedFinalUrl(connection.getURL())) {
                    throw new SecurityException("Update download was redirected to an untrusted location");
                }
                long declaredLength = connection.getContentLength();
                if (declaredLength <= 0 || declaredLength > MAX_APK_BYTES) {
                    throw new SecurityException("Invalid update size");
                }

                long downloaded = 0;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    int lastPercent = -1;
                    while ((read = input.read(buffer)) != -1) {
                        downloaded += read;
                        if (downloaded > MAX_APK_BYTES) {
                            throw new SecurityException("Update exceeds size limit");
                        }
                        output.write(buffer, 0, read);
                        int percent = (int) Math.min(100, downloaded * 100 / declaredLength);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            int progress = percent;
                            mainHandler.post(() -> callback.onProgress(progress));
                        }
                    }
                    output.getFD().sync();
                } finally {
                    connection.disconnect();
                }
                if (downloaded != declaredLength
                        || !ApkIntegrity.matchesSha256(partial, config.sha256)
                        || !isExpectedSignedUpdate(partial, config)) {
                    throw new SecurityException("Update verification failed");
                }
                if (target.exists() && !target.delete()) {
                    throw new IllegalStateException("Cannot replace cached update");
                }
                if (!partial.renameTo(target)) {
                    throw new IllegalStateException("Cannot finalize update");
                }
                mainHandler.post(() -> callback.onReady(target));
            } catch (Exception ignored) {
                if (partial != null && partial.exists()) {
                    // A failed or incomplete APK must never reach the package installer.
                    partial.delete();
                }
                mainHandler.post(callback::onError);
            }
        });
    }

    void installOrRequestPermission(Activity activity, File apk, MobileAppConfig config) {
        try {
            if (!ApkIntegrity.matchesSha256(apk, config.sha256) || !isExpectedSignedUpdate(apk, config)) {
                throw new SecurityException("Cached update verification failed");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                preferences().edit().putString(PENDING_APK, apk.getAbsolutePath()).apply();
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return;
            }
            preferences().edit().remove(PENDING_APK).apply();
            launchSystemInstaller(activity, apk);
        } catch (Exception ignored) {
            preferences().edit().remove(PENDING_APK).apply();
            throw new IllegalStateException("The downloaded update could not be verified.", ignored);
        }
    }

    boolean resumePendingInstall(Activity activity, MobileAppConfig config) {
        String path = preferences().getString(PENDING_APK, null);
        if (path == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls())) {
            return false;
        }
        installOrRequestPermission(activity, new File(path), config);
        return true;
    }

    void close() {
        executor.shutdown();
    }

    @SuppressWarnings("deprecation")
    private boolean isExpectedSignedUpdate(File apk, MobileAppConfig config) throws Exception {
        PackageManager manager = context.getPackageManager();
        int signatureFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), signatureFlags);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), signatureFlags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || config.latestVersionCode == null || versionCode(archive) != config.latestVersionCode
                || versionCode(archive) <= BuildConfig.VERSION_CODE) {
            return false;
        }

        Signature[] archiveSignatures = signatures(archive);
        Signature[] installedSignatures = signatures(installed);
        for (Signature candidate : archiveSignatures) {
            for (Signature trusted : installedSignatures) {
                if (Arrays.equals(candidate.toByteArray(), trusted.toByteArray())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            return info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        }
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    private void launchSystemInstaller(Activity activity, File apk) {
        Uri contentUri = FileProvider.getUriForFile(
                activity, activity.getPackageName() + ".files", apk);
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
    }

    private static boolean isTrustedFinalUrl(URL url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(url.getProtocol())
                && ("github.com".equals(host) || host.endsWith(".githubusercontent.com"));
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
