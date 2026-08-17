package com.example.viewonlybrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches signed controls and sends a minimal, privacy-safe event record. */
final class MobileControlClient {
    interface ConfigCallback {
        void onLoaded(MobileAppConfig config, boolean fresh);
    }

    private static final String PREFERENCES = "mobile_control";
    private static final String CACHED_ENVELOPE = "verified_envelope";
    private static final String INSTALLATION_ID = "installation_id";
    private static final int CONNECT_TIMEOUT_MS = 7000;
    private static final int READ_TIMEOUT_MS = 7000;

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    MobileControlClient(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    MobileAppConfig cachedOrSafeConfig() {
        String cached = preferences.getString(CACHED_ENVELOPE, null);
        if (cached != null) {
            try {
                return MobileAppConfig.verifyAndParse(cached);
            } catch (Exception ignored) {
                preferences.edit().remove(CACHED_ENVELOPE).apply();
            }
        }
        return MobileAppConfig.safeDefaults();
    }

    void fetchConfig(ConfigCallback callback) {
        executor.execute(() -> {
            MobileAppConfig fallback = cachedOrSafeConfig();
            try {
                HttpURLConnection connection = open("/config", "GET");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Configuration HTTP " + status);
                }
                String envelope = readBody(connection);
                MobileAppConfig fresh = MobileAppConfig.verifyAndParse(envelope);
                if (fresh.configVersion < fallback.configVersion) {
                    throw new SecurityException("Configuration version went backwards");
                }
                preferences.edit().putString(CACHED_ENVELOPE, envelope).apply();
                mainHandler.post(() -> callback.onLoaded(fresh, true));
            } catch (Exception ignored) {
                sendEvent("config_failed", fallback);
                mainHandler.post(() -> callback.onLoaded(fallback, false));
            }
        });
    }

    void sendEvent(String eventType, MobileAppConfig config) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject event = new JSONObject();
                event.put("installation_id", installationId());
                event.put("event_type", eventType);
                event.put("app_version_name", BuildConfig.VERSION_NAME);
                event.put("app_version_code", BuildConfig.VERSION_CODE);
                event.put("device_occurred_at", localIsoTimestamp());
                event.put("timezone", TimeZone.getDefault().getID());
                event.put("readonly_enabled", config.readonlyEnabled);
                event.put("function_blocking_enabled", config.functionBlockingEnabled);
                byte[] body = event.toString().getBytes(StandardCharsets.UTF_8);

                connection = open("/events", "POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
                // Telemetry must never stop login or weaken the protection controls.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    void close() {
        executor.shutdown();
    }

    private HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                BuildConfig.CONTROL_API_BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return body.toString();
    }

    private String installationId() {
        String existing = preferences.getString(INSTALLATION_ID, null);
        if (existing != null) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        preferences.edit().putString(INSTALLATION_ID, created).apply();
        return created;
    }

    private static String localIsoTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        String compactOffset = format.format(new Date());

        return compactOffset.substring(0, compactOffset.length() - 2)
                + ":" + compactOffset.substring(compactOffset.length() - 2);
    }
}
