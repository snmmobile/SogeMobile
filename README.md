# SogeMobile

A small Android app with a homepage button that opens Sogebanking in an integrated browser. Version 1.4 loads a signed remote configuration from the portfolio administration service. When the authenticated account dashboard is detected, the administrator can independently enable **view-only mode**, **sensitive-function blocking**, and a clearly marked temporary display override managed from the admin dashboard:

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

The login journey remains interactive. As soon as the permanent dashboard labels appear, the page installs whichever protections are enabled before notifying Android. Read-only mode blocks general taps, forms, and navigation while preserving vertical scrolling. Sensitive-function blocking allows the site's `load_account(...)` and `load_content(...)` functions to run while transfer/account-detail controls remain hidden. The exact `pages_personal/account_details.html` and `pages_personal/transfers_landing.html` documents are intercepted before their responses or main-frame navigations can render while sensitive-function blocking is active. Both the root and `/sogebanking/` path forms are covered.

## Remote administration and privacy

The app requests `https://bernadomyrtil.com/api/mobile/v1/config` when it launches. It accepts a response only when its ECDSA signature matches the public key embedded in the build, rejects lower configuration versions, and caches the last verified response. If no verified response exists, safe defaults keep both protection modes enabled. The control service can also disable the app with a maintenance message and publish optional or mandatory GitHub APK updates.

The app sends privacy-safe `app_open`, `dashboard_detected`, configuration-failure, and update-prompt events. Each event contains a random installation UUID, app version, device date/time and timezone, and the active protection flags. The server hashes the UUID before storage. No bank username, password, customer name, account number, balance, or page content is collected. A dashboard-detection event means the stable page labels were visible; it is not a bank-authentication record.

The private configuration-signing key belongs outside both repositories under `Documents\apps\signing`. Only its public key is embedded in this project. The first remote-enabled APK is version code 2 (`1.1`); already-installed version 1 cannot receive remote controls and must be updated manually once.

## In-app updates

When the signed configuration advertises a higher `version_code`, SogeMobile warns the user about the update. After the user chooses **Download update**, the app downloads the APK directly into its private cache, verifies the signed-config SHA-256 digest, package ID, version code, and release signing certificate, then opens Android's system installer. Android 8 and later may first ask the user to allow installs from SogeMobile.

Android requires the user to confirm the final installation on ordinary consumer devices. Silent installation is intentionally unavailable unless the app is a device owner/profile owner on a managed device. Failed, oversized, altered, wrong-package, wrong-version, or differently signed APKs are deleted and never passed to the installer.

The update source is restricted to direct APK assets under `github.com/snmmobile/SogeMobile/releases/download/`. `REQUEST_INSTALL_PACKAGES` is declared because releases are distributed from GitHub rather than Google Play.

## Local Git workflow

The canonical local checkout is `C:\Users\HP\Documents\custom\SogeMobile` and its `origin` is `https://github.com/snmmobile/SogeMobile.git` on branch `main`.

For a future release:

1. Make source changes in this checkout and run `gradlew.bat testDebugUnitTest lintDebug assembleDebug`.
2. Increase `versionCode` and `versionName` in `app/build.gradle`.
3. Commit and push the source changes.
4. Run `scripts/build-signed-release.ps1` with a destination matching the version.
5. Create the GitHub release, upload the signed APK, and record its direct URL and SHA-256 in the SogeMobile admin control center.
6. Select the release as latest; set `minimum_version_code` and `force_update` only when older builds must be blocked.

## Build

1. Open this folder in Android Studio.
2. Allow Gradle sync to finish and install Android SDK 35 if prompted.
3. Run the `app` configuration on an emulator or Android device (Android 6.0 or newer).

From a terminal, use `gradlew.bat testDebugUnitTest assembleDebug`. The APK will be written to `app/build/outputs/apk/debug/sogemobile.apk`.

For a public release, run `powershell -ExecutionPolicy Bypass -File scripts/build-signed-release.ps1`. The first run creates a permanent release keystore outside the repository under `Documents\apps\signing`, prompts for its password without saving it, runs tests and release lint, builds the signed APK, and verifies its certificate. Back up the keystore separately and keep its password in a password manager; losing either one prevents publishing updates under the same Android application identity.

## Security note

This app is a presentation control, not a digital-rights-management system. It prevents normal WebView interaction and navigation after the target page loads, but content delivered to a user-controlled device can still be inspected by a sufficiently determined device owner. Sensitive content still needs server-side authentication and authorization.
