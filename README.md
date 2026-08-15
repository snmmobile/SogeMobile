# SogeMobile

A small Android app with a homepage button that opens Sogebanking in an integrated browser. When the authenticated account dashboard is detected, the browser enters **view-only mode**:

The public Android application ID is `com.snmmobile.sogemobile`.

- links, buttons, forms, media controls, and other interactive page elements are disabled;
- main-frame navigation attempts, redirects, custom URL schemes, and invalid TLS connections are blocked;
- reading and vertical scrolling remain available;
- the app close button and Android Back button still return the user to the homepage, so the user is never trapped.

## Current website configuration

The `debug` and `release` build types use:

```gradle
buildConfigField "String", "START_URL", '"https://www2.sogebanking.com/sogebanking/#f"'
buildConfigField "String", "TRUSTED_HOST", '"sogebanking.com"'
buildConfigField "String", "REDIRECT_HOST", '"www2.sogebanking.com"'
```

`START_URL` is opened by the homepage button. Dashboard detection runs only on the configured Sogebanking HTTPS hosts (including `www`, `www2`, and the `/sogebanking/` routes). The authenticated account-list view is identified by a combination of permanent page labels (`Comptes de dépôt`, `Se déconnecter`, and `Bienvenue`), without reading or storing any customer name, account number, balance, date, or other personal data from the page.

The login journey remains interactive. As soon as the permanent dashboard labels appear, the page installs its blocker before notifying Android that view-only mode is active. Account-expansion, transfer-option, and account-detail controls are hidden; Android-level tap suppression, page-level interaction blocking, and no-op replacements for `load_account(...)` and `load_content(...)` prevent account selection and navigation while vertical scrolling remains available. The exact `pages_personal/account_details.html` document is always intercepted with an empty response, so it cannot be rendered even if a background request races dashboard detection.

## Build

1. Open this folder in Android Studio.
2. Allow Gradle sync to finish and install Android SDK 35 if prompted.
3. Run the `app` configuration on an emulator or Android device (Android 6.0 or newer).

From a terminal, use `gradlew.bat testDebugUnitTest assembleDebug`. The APK will be written to `app/build/outputs/apk/debug/sogemobile.apk`.

For a public release, run `powershell -ExecutionPolicy Bypass -File scripts/build-signed-release.ps1`. The first run creates a permanent release keystore outside the repository under `Documents\apps\signing`, prompts for its password without saving it, runs tests and release lint, builds the signed APK, and verifies its certificate. Back up the keystore separately and keep its password in a password manager; losing either one prevents publishing updates under the same Android application identity.

## Security note

This app is a presentation control, not a digital-rights-management system. It prevents normal WebView interaction and navigation after the target page loads, but content delivered to a user-controlled device can still be inspected by a sufficiently determined device owner. Sensitive content still needs server-side authentication and authorization.
