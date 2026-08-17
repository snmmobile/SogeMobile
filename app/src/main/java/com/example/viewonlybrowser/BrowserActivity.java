package com.example.viewonlybrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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

public final class BrowserActivity extends Activity {
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
    private float touchDownX;
    private float touchDownY;
    private boolean isTapGesture;
    private int touchSlop;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        ImageButton closeButton = findViewById(R.id.closeButton);
        controlClient = new MobileControlClient(this);
        config = controlClient.cachedOrSafeConfig();

        closeButton.setOnClickListener(view -> finish());

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new LockedPageWebViewClient());
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        webView.setOnLongClickListener(view -> dashboardDetected && config.readonlyEnabled);
        webView.setOnTouchListener((view, event) -> blockTapWhileAllowingScroll(event));

        if (savedInstanceState == null) {
            webView.loadUrl(config.startUrl);
        } else {
            dashboardDetected = savedInstanceState.getBoolean(STATE_DASHBOARD_DETECTED, false);
            String currentUrl = savedInstanceState.getString(STATE_CURRENT_URL, config.startUrl);
            updateProtectionUi();
            webView.loadUrl(currentUrl);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DASHBOARD_DETECTED, dashboardDetected);
        outState.putString(STATE_CURRENT_URL, webView.getUrl());
    }

    @Override
    @SuppressWarnings("deprecation") // Native Activity compatibility for Android 6-12 without an AndroidX dependency.
    public void onBackPressed() {
        if (!(dashboardDetected && config.readonlyEnabled) && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
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
            controlClient.sendEvent("dashboard_detected", config);
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

    private boolean blockTapWhileAllowingScroll(MotionEvent event) {
        if (!dashboardDetected || !config.readonlyEnabled) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                isTapGesture = true;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - touchDownX) > touchSlop
                        || Math.abs(event.getY() - touchDownY) > touchSlop) {
                    isTapGesture = false;
                }
                return false;
            case MotionEvent.ACTION_UP:
                boolean shouldBlockTap = isTapGesture;
                isTapGesture = false;
                return shouldBlockTap;
            case MotionEvent.ACTION_CANCEL:
                isTapGesture = false;
                return false;
            default:
                return false;
        }
    }

    private void injectTargetPageDetector() {
        webView.evaluateJavascript(ViewOnlyScripts.targetDetector(
                TARGET_DETECTED_URL, config.readonlyEnabled, config.functionBlockingEnabled), null);
    }

    private void injectInteractionBlocker() {
        webView.evaluateJavascript(ViewOnlyScripts.interactionBlocker(
                config.readonlyEnabled, config.functionBlockingEnabled), null);
    }

    private final class LockedPageWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (config.functionBlockingEnabled && ViewOnlyBlockPolicy.blocks(request.getUrl().toString())) {
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
            } else if (trustedSitePolicy.matches(url)) {
                injectTargetPageDetector();
            }
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (dashboardDetected) {
                injectInteractionBlocker();
            } else if (trustedSitePolicy.matches(url)) {
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
