package com.example.viewonlybrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;

public final class BrowserActivity extends Activity {
    static final String EXTRA_STATIC_DASHBOARD_TEST = "static_dashboard_test";
    private static final String STATE_CURRENT_URL = "current_url";
    private static final String STATE_DASHBOARD_DETECTED = "dashboard_detected";
    private static final String TARGET_DETECTED_URL = "viewonly://target-detected";

    private final TrustedSitePolicy trustedSitePolicy = new TrustedSitePolicy(
            BuildConfig.TRUSTED_HOST,
            BuildConfig.REDIRECT_HOST);

    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    private MobileControlClient controlClient;
    private MobileAppConfig config;
    private boolean dashboardDetected;
    private boolean staticDashboardTest;
    private AppUnlockGate unlockGate;
    private Bundle restoredState;
    private boolean initialized;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_browser);

        restoredState = savedInstanceState;
        View protectedContent = findViewById(R.id.browserRoot);
        protectedContent.setVisibility(View.INVISIBLE);
        unlockGate = new AppUnlockGate(this, protectedContent, this::initializeBrowserIfNeeded);
    }

    @Override
    protected void onStart() {
        super.onStart();
        unlockGate.requireUnlock();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initializeBrowserIfNeeded() {
        if (initialized) {
            return;
        }
        initialized = true;

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        ImageButton closeButton = findViewById(R.id.closeButton);
        controlClient = new MobileControlClient(this);
        staticDashboardTest = BuildConfig.DEBUG
                && getIntent().getBooleanExtra(EXTRA_STATIC_DASHBOARD_TEST, false);
        config = staticDashboardTest
                ? StaticDashboardFixture.config()
                : controlClient.cachedOrSafeConfig();

        closeButton.setOnClickListener(view -> finish());

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new DashboardBridge(), "SogeMobileBridge");
        webView.setWebViewClient(new LockedPageWebViewClient());
        webView.setOnLongClickListener(view -> dashboardDetected && config.readonlyEnabled);

        if (restoredState == null) {
            loadInitialPage();
        } else {
            dashboardDetected = restoredState.getBoolean(STATE_DASHBOARD_DETECTED, false);
            String currentUrl = restoredState.getString(STATE_CURRENT_URL, config.startUrl);
            updateProtectionUi();
            if (staticDashboardTest) {
                loadStaticDashboard();
            } else {
                webView.loadUrl(currentUrl);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DASHBOARD_DETECTED, dashboardDetected);
        if (webView != null) {
            outState.putString(STATE_CURRENT_URL, webView.getUrl());
        }
    }

    @Override
    @SuppressWarnings("deprecation") // Native Activity compatibility for Android 6-12 without an AndroidX dependency.
    public void onBackPressed() {
        if (webView != null && config != null
                && !(dashboardDetected && config.readonlyEnabled) && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (unlockGate != null && unlockGate.handleActivityResult(requestCode, resultCode)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("SogeMobileBridge");
            webView.setWebViewClient(null);
            webView.destroy();
        }
        if (controlClient != null) {
            controlClient.close();
        }
        super.onDestroy();
    }

    private void activateDashboardProtection() {
        if (!dashboardDetected) {
            dashboardDetected = true;
            if (config.readonlyEnabled) {
                webView.clearHistory();
            }
            updateProtectionUi();
            if (!staticDashboardTest) {
                controlClient.sendEvent("dashboard_detected", config);
            }
        }
    }

    private void loadInitialPage() {
        if (staticDashboardTest) {
            loadStaticDashboard();
        } else {
            webView.loadUrl(config.startUrl);
        }
    }

    private void loadStaticDashboard() {
        try (InputStream input = getAssets().open(StaticDashboardFixture.ASSET_NAME);
             Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            String html = scanner.hasNext() ? scanner.next() : "";
            webView.loadDataWithBaseURL(
                    StaticDashboardFixture.BASE_URL, html, "text/html", "UTF-8", null);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.page_load_failed, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void updateProtectionUi() {
        if (!dashboardDetected) {
            statusText.setText(R.string.secure_browser);
        } else if (config.readonlyEnabled) {
            statusText.setText(R.string.view_only_mode);
        } else if (config.functionBlockingEnabled) {
            statusText.setText(R.string.function_protection_mode);
        } else {
            statusText.setText(R.string.protected_browser);
        }
    }

    private void injectTargetPageDetector() {
        webView.evaluateJavascript(ViewOnlyScripts.targetDetector(
                TARGET_DETECTED_URL, config.readonlyEnabled, config.functionBlockingEnabled), null);
    }

    private void injectInteractionBlocker() {
        webView.evaluateJavascript(ViewOnlyScripts.interactionBlocker(
                config.readonlyEnabled, config.functionBlockingEnabled), null);
        injectTemporaryAccountDisplayOverride();
    }

    private void injectTemporaryAccountDisplayOverride() {
        if (!config.displayOverrideEnabled) {
            return;
        }

        webView.evaluateJavascript(ViewOnlyScripts.temporaryAccountDisplayOverride(
                config.displayOverrideSalt,
                config.displayOverrideAccountHash,
                config.displayOverrideBalanceText), null);
    }

    /** Receives only the dashboard-detected signal; no page or account data crosses the bridge. */
    private final class DashboardBridge {
        @JavascriptInterface
        public void onDashboardDetected() {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || webView == null) {
                    return;
                }
                activateDashboardProtection();
                injectInteractionBlocker();
            });
        }
    }

    private final class LockedPageWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String requestedUrl = request.getUrl().toString();
            boolean staticSensitiveRequest = staticDashboardTest
                    && StaticDashboardFixture.blocks(requestedUrl);
            if (config.functionBlockingEnabled
                    && (ViewOnlyBlockPolicy.blocks(requestedUrl) || staticSensitiveRequest)) {
                if (staticSensitiveRequest) {
                    runOnUiThread(() -> view.evaluateJavascript(
                            "window.__sogemobileStaticBlocked&&window.__sogemobileStaticBlocked();",
                            null));
                }
                return new WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return false;
            }

            String requestedUrl = request.getUrl().toString();
            if (TARGET_DETECTED_URL.equals(requestedUrl)) {
                activateDashboardProtection();
                injectInteractionBlocker();
                return true;
            }

            // Do not invoke or replace the site's function. Let its own exact
            // account-list trigger proceed unchanged; sensitive response URLs
            // remain governed by ViewOnlyBlockPolicy below.
            if (dashboardDetected && SiteJavascriptPolicy.allowsAccountLoad(requestedUrl)) {
                return false;
            }

            // Let the site's JavaScript loaders run, but never allow an explicitly
            // sensitive account-details or transfer destination to become the main document.
            if (config.functionBlockingEnabled && (ViewOnlyBlockPolicy.blocks(requestedUrl)
                    || (staticDashboardTest && StaticDashboardFixture.blocks(requestedUrl)))) {
                return true;
            }

            if (dashboardDetected && config.readonlyEnabled) {
                Toast.makeText(BrowserActivity.this, R.string.navigation_disabled, Toast.LENGTH_SHORT).show();
                return true;
            }

            if (!"https".equalsIgnoreCase(request.getUrl().getScheme())) {
                Toast.makeText(BrowserActivity.this, R.string.only_https_allowed, Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            if (dashboardDetected) {
                injectInteractionBlocker();
            } else if (staticDashboardTest || trustedSitePolicy.matches(url)) {
                injectTargetPageDetector();
            }
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (dashboardDetected) {
                injectInteractionBlocker();
            } else if (staticDashboardTest || trustedSitePolicy.matches(url)) {
                injectTargetPageDetector();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(BrowserActivity.this, R.string.page_load_failed, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            progressBar.setVisibility(View.GONE);
            Toast.makeText(BrowserActivity.this, R.string.secure_connection_failed, Toast.LENGTH_LONG).show();
        }
    }
}
