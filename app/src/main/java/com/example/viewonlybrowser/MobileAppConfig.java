package com.example.viewonlybrowser;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;

/** Verified remote controls. An invalid or unavailable response never weakens the safe defaults. */
final class MobileAppConfig {
    final int configVersion;
    final boolean appEnabled;
    final String maintenanceMessage;
    final boolean readonlyEnabled;
    final boolean functionBlockingEnabled;
    final boolean displayOverrideEnabled;
    final String displayOverrideSalt;
    final String displayOverrideAccountHash;
    final String displayOverrideBalanceText;
    final String startUrl;
    final int minimumVersionCode;
    final boolean forceUpdate;
    final Integer latestVersionCode;
    final String latestVersionName;
    final String downloadUrl;
    final String sha256;
    final String releaseNotes;

    MobileAppConfig(
            int configVersion,
            boolean appEnabled,
            String maintenanceMessage,
            boolean readonlyEnabled,
            boolean functionBlockingEnabled,
            boolean displayOverrideEnabled,
            String displayOverrideSalt,
            String displayOverrideAccountHash,
            String displayOverrideBalanceText,
            String startUrl,
            int minimumVersionCode,
            boolean forceUpdate,
            Integer latestVersionCode,
            String latestVersionName,
            String downloadUrl,
            String sha256,
            String releaseNotes) {
        this.configVersion = configVersion;
        this.appEnabled = appEnabled;
        this.maintenanceMessage = maintenanceMessage;
        this.readonlyEnabled = readonlyEnabled;
        this.functionBlockingEnabled = functionBlockingEnabled;
        this.displayOverrideEnabled = displayOverrideEnabled;
        this.displayOverrideSalt = displayOverrideSalt;
        this.displayOverrideAccountHash = displayOverrideAccountHash;
        this.displayOverrideBalanceText = displayOverrideBalanceText;
        this.startUrl = startUrl;
        this.minimumVersionCode = minimumVersionCode;
        this.forceUpdate = forceUpdate;
        this.latestVersionCode = latestVersionCode;
        this.latestVersionName = latestVersionName;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.releaseNotes = releaseNotes;
    }

    static MobileAppConfig safeDefaults() {
        return new MobileAppConfig(0, true, "", true, true,
                false, null, null, null, BuildConfig.START_URL,
                1, false, null, null, null, null, null);
    }

    static MobileAppConfig verifyAndParse(String envelopeJson) throws Exception {
        JSONObject envelope = new JSONObject(envelopeJson);
        if (!"SHA256withECDSA".equals(envelope.optString("algorithm"))
                || !BuildConfig.CONFIG_SIGNING_KEY_ID.equals(envelope.optString("key_id"))) {
            throw new SecurityException("Unknown mobile configuration signer");
        }

        byte[] payloadBytes = Base64.decode(envelope.getString("payload"), Base64.DEFAULT);
        byte[] signatureBytes = Base64.decode(envelope.getString("signature"), Base64.DEFAULT);
        PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(
                Base64.decode(BuildConfig.CONFIG_PUBLIC_KEY_BASE64, Base64.DEFAULT)));
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(payloadBytes);
        if (!verifier.verify(signatureBytes)) {
            throw new SecurityException("Invalid mobile configuration signature");
        }

        return parsePayload(new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8)));
    }

    static MobileAppConfig parsePayload(JSONObject payload) throws Exception {
        if (payload.getInt("schema_version") != 1) {
            throw new SecurityException("Unsupported mobile configuration schema");
        }

        JSONObject app = payload.getJSONObject("app");
        JSONObject protections = payload.getJSONObject("protections");
        JSONObject displayOverride = payload.optJSONObject("display_override");
        JSONObject web = payload.getJSONObject("web");
        JSONObject update = payload.getJSONObject("update");
        String startUrl = web.getString("start_url");
        if (!new TrustedSitePolicy(BuildConfig.TRUSTED_HOST, BuildConfig.REDIRECT_HOST).matches(startUrl)) {
            throw new SecurityException("Untrusted start URL");
        }

        String downloadUrl = nullableString(update, "download_url");
        if (downloadUrl != null && !ReleaseDownloadPolicy.isTrusted(downloadUrl)) {
            throw new SecurityException("Untrusted APK download URL");
        }
        String digest = nullableString(update, "sha256");
        if (digest != null && !digest.matches("(?i)[a-f0-9]{64}")) {
            throw new SecurityException("Invalid APK digest");
        }

        boolean displayOverrideEnabled = displayOverride != null
                && displayOverride.optBoolean("enabled", false);
        String displayOverrideSalt = null;
        String displayOverrideAccountHash = null;
        String displayOverrideBalanceText = null;
        if (displayOverrideEnabled) {
            displayOverrideSalt = nullableString(displayOverride, "account_salt");
            displayOverrideAccountHash = nullableString(displayOverride, "account_hash");
            displayOverrideBalanceText = nullableString(displayOverride, "balance_text");
            if (displayOverrideSalt == null || !displayOverrideSalt.matches("(?i)[a-f0-9]{32}")
                    || displayOverrideAccountHash == null
                    || !displayOverrideAccountHash.matches("(?i)[a-f0-9]{64}")
                    || displayOverrideBalanceText == null
                    || displayOverrideBalanceText.length() > 40) {
                throw new SecurityException("Invalid demo display override");
            }
        }

        return new MobileAppConfig(
                payload.getInt("config_version"),
                app.getBoolean("enabled"),
                app.optString("maintenance_message", ""),
                protections.getBoolean("readonly_enabled"),
                protections.getBoolean("function_blocking_enabled"),
                displayOverrideEnabled,
                displayOverrideSalt == null ? null : displayOverrideSalt.toLowerCase(Locale.ROOT),
                displayOverrideAccountHash == null ? null : displayOverrideAccountHash.toLowerCase(Locale.ROOT),
                displayOverrideBalanceText,
                startUrl,
                Math.max(1, update.getInt("minimum_version_code")),
                update.getBoolean("force_update"),
                update.isNull("version_code") ? null : update.getInt("version_code"),
                nullableString(update, "version_name"),
                downloadUrl,
                digest == null ? null : digest.toLowerCase(Locale.ROOT),
                nullableString(update, "release_notes"));
    }

    private static String nullableString(JSONObject object, String key) {
        if (object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    boolean hasUpdate() {
        return latestVersionCode != null
                && latestVersionCode > BuildConfig.VERSION_CODE
                && downloadUrl != null
                && sha256 != null;
    }

    boolean requiresUpdate() {
        return hasUpdate() && forceUpdate && BuildConfig.VERSION_CODE < minimumVersionCode;
    }
}
