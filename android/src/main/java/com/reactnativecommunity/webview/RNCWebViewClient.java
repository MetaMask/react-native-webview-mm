package com.reactnativecommunity.webview;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.reactnativecommunity.webview.events.TopHttpErrorEvent;
import com.reactnativecommunity.webview.events.TopLoadingErrorEvent;
import com.reactnativecommunity.webview.events.TopLoadingFinishEvent;
import com.reactnativecommunity.webview.events.TopLoadingStartEvent;
import com.reactnativecommunity.webview.events.TopRenderProcessGoneEvent;
import com.reactnativecommunity.webview.events.TopShouldStartLoadWithRequestEvent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class RNCWebViewClient extends WebViewClient {
    private static String TAG = "RNCWebViewClient";
    protected static final int SHOULD_OVERRIDE_URL_LOADING_TIMEOUT = 250;

    protected boolean mLastLoadFailed = false;
    protected RNCWebView.ProgressChangedFilter progressChangedFilter = null;
    protected @Nullable String ignoreErrFailedForThisURL = null;
    protected @Nullable RNCBasicAuthCredential basicAuthCredential = null;

    protected boolean mE2EMode = false;
    protected @Nullable String mMockServerUrl = null;

    public void setIgnoreErrFailedForThisURL(@Nullable String url) {
        ignoreErrFailedForThisURL = url;
    }

    public void setBasicAuthCredential(@Nullable RNCBasicAuthCredential credential) {
        basicAuthCredential = credential;
    }

    public void setE2EMode(boolean enabled) {
        mE2EMode = enabled;
        Log.d(TAG, "[E2E] E2E mode set to: " + enabled);
    }

    public void setMockServerUrl(@Nullable String url) {
        mMockServerUrl = url;
        Log.d(TAG, "[E2E] Mock server URL set to: " + url);
    }

    @Override
    public void onPageFinished(WebView webView, String url) {
        super.onPageFinished(webView, url);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush();
            }
        }

        if (!mLastLoadFailed) {
            RNCWebView reactWebView = (RNCWebView) webView;

            RNCWebChromeClient webChromeClient = (RNCWebChromeClient) reactWebView.getWebChromeClient();
            if (Objects.nonNull(webChromeClient))
                webChromeClient.blockJsDuringLoading = false;

            reactWebView.callInjectedJavaScript();

            reactWebView.injectBlobFileDownloaderScript();

            reactWebView.injectIFrameDetectorScript();

            emitFinishEvent(webView, url);
        }
    }

    @Override
    public void doUpdateVisitedHistory(WebView webView, String url, boolean isReload) {
        super.doUpdateVisitedHistory(webView, url, isReload);

        ((RNCWebView) webView).dispatchEvent(
                webView,
                new TopLoadingStartEvent(
                        RNCWebViewWrapper.getReactTagFromWebView(webView),
                        createWebViewEvent(webView, url)));
    }

    @Override
    public void onPageStarted(WebView webView, String url, Bitmap favicon) {
        super.onPageStarted(webView, url, favicon);
        mLastLoadFailed = false;

        RNCWebView reactWebView = (RNCWebView) webView;

        RNCWebChromeClient webChromeClient = (RNCWebChromeClient) reactWebView.getWebChromeClient();
        if (Objects.nonNull(webChromeClient))
            webChromeClient.blockJsDuringLoading = true;

        reactWebView.callInjectedJavaScriptBeforeContentLoaded();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        final RNCWebView rncWebView = (RNCWebView) view;

        RNCWebChromeClient webChromeClient = (RNCWebChromeClient) rncWebView.getWebChromeClient();
        if (Objects.nonNull(webChromeClient))
            webChromeClient.blockJsDuringLoading = true;

        final boolean isJsDebugging = rncWebView.getReactApplicationContext().getJavaScriptContextHolder().get() == 0;

        if (!isJsDebugging && rncWebView.mMessagingJSModule != null) {
            final Pair<Double, AtomicReference<RNCWebViewModuleImpl.ShouldOverrideUrlLoadingLock.ShouldOverrideCallbackState>> lock = RNCWebViewModuleImpl.shouldOverrideUrlLoadingLock
                    .getNewLock();
            final double lockIdentifier = lock.first;
            final AtomicReference<RNCWebViewModuleImpl.ShouldOverrideUrlLoadingLock.ShouldOverrideCallbackState> lockObject = lock.second;

            final WritableMap event = createWebViewEvent(view, url);
            event.putDouble("lockIdentifier", lockIdentifier);
            rncWebView.dispatchDirectShouldStartLoadWithRequest(event);

            try {
                assert lockObject != null;
                synchronized (lockObject) {
                    final long startTime = SystemClock.elapsedRealtime();
                    while (lockObject
                            .get() == RNCWebViewModuleImpl.ShouldOverrideUrlLoadingLock.ShouldOverrideCallbackState.UNDECIDED) {
                        if (SystemClock.elapsedRealtime() - startTime > SHOULD_OVERRIDE_URL_LOADING_TIMEOUT) {
                            FLog.w(TAG,
                                    "Did not receive response to shouldOverrideUrlLoading in time, defaulting to allow loading.");
                            RNCWebViewModuleImpl.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);
                            return false;
                        }
                        lockObject.wait(SHOULD_OVERRIDE_URL_LOADING_TIMEOUT);
                    }
                }
            } catch (InterruptedException e) {
                FLog.e(TAG, "shouldOverrideUrlLoading was interrupted while waiting for result.", e);
                RNCWebViewModuleImpl.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);
                return false;
            }

            final boolean shouldOverride = lockObject
                    .get() == RNCWebViewModuleImpl.ShouldOverrideUrlLoadingLock.ShouldOverrideCallbackState.SHOULD_OVERRIDE;
            RNCWebViewModuleImpl.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);

            return shouldOverride;
        } else {
            FLog.w(TAG,
                    "Couldn't use blocking synchronous call for onShouldStartLoadWithRequest due to debugging or missing Catalyst instance, falling back to old event-and-load.");
            progressChangedFilter.setWaitingForCommandLoadUrl(true);

            int reactTag = RNCWebViewWrapper.getReactTagFromWebView(view);
            UIManagerHelper.getEventDispatcherForReactTag((ReactContext) view.getContext(), reactTag)
                    .dispatchEvent(new TopShouldStartLoadWithRequestEvent(
                            reactTag,
                            createWebViewEvent(view, url)));
            return true;
        }
    }

    /**
     * E2E Testing: Intercept all resource requests and route through mock server proxy.
     * This allows the mock server to intercept and mock/block any network request from the WebView.
     */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Only intercept in E2E mode with a valid mock server URL
        if (!mE2EMode || mMockServerUrl == null) {
            return super.shouldInterceptRequest(view, request);
        }

        String originalUrl = request.getUrl().toString();

        // Skip interception for localhost/mock server URLs to avoid infinite loops
        if (isLocalOrMockServerUrl(originalUrl)) {
            return super.shouldInterceptRequest(view, request);
        }

        // Skip non-HTTP(S) URLs
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            return super.shouldInterceptRequest(view, request);
        }

        try {
            // Route through mock server proxy (same pattern as shim.js)
            String proxyUrl = mMockServerUrl + "/proxy?url=" + URLEncoder.encode(originalUrl, "UTF-8");
            Log.d(TAG, "[E2E] Intercepting request: " + originalUrl + " -> " + proxyUrl);

            URL url = new URL(proxyUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            // Copy headers from original request
            Map<String, String> requestHeaders = request.getRequestHeaders();
            if (requestHeaders != null) {
                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                    // Skip host header as it will be set automatically
                    if (!"Host".equalsIgnoreCase(header.getKey())) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
            }

            connection.connect();

            int statusCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            String encoding = connection.getContentEncoding();

            // Get the appropriate input stream based on response code
            InputStream inputStream;
            if (statusCode >= 400) {
                inputStream = connection.getErrorStream();
                if (inputStream == null) {
                    inputStream = connection.getInputStream();
                }
            } else {
                inputStream = connection.getInputStream();
            }

            // Parse content type and charset
            String mimeType = "text/html";
            String charset = "UTF-8";
            if (contentType != null) {
                String[] parts = contentType.split(";");
                mimeType = parts[0].trim();
                for (String part : parts) {
                    if (part.trim().toLowerCase().startsWith("charset=")) {
                        charset = part.trim().substring(8);
                    }
                }
            }

            Log.d(TAG, "[E2E] Proxied response: status=" + statusCode + ", type=" + mimeType);
            return new WebResourceResponse(mimeType, charset, statusCode,
                    connection.getResponseMessage(), connection.getHeaderFields().entrySet().stream()
                    .filter(e -> e.getKey() != null)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))),
                    inputStream);

        } catch (Exception e) {
            Log.w(TAG, "[E2E] Failed to proxy request, falling back to original: " + e.getMessage());
            // Fallback to original request on error
            return super.shouldInterceptRequest(view, request);
        }
    }

    /**
     * Check if URL is local or pointing to mock server (should not be proxied)
     */
    private boolean isLocalOrMockServerUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            return "localhost".equals(host) ||
                "127.0.0.1".equals(host) ||
                "10.0.2.2".equals(host) ||
                (mMockServerUrl != null && url.startsWith(mMockServerUrl));
        } catch (Exception e) {
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final String url = request.getUrl().toString();
        return this.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        if (basicAuthCredential != null) {
            handler.proceed(basicAuthCredential.username, basicAuthCredential.password);
            return;
        }
        super.onReceivedHttpAuthRequest(view, handler, host, realm);
    }

    @Override
    public void onReceivedSslError(final WebView webView, final SslErrorHandler handler, final SslError error) {
        // onReceivedSslError is called for most requests, per Android docs:
        // https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,%2520android.webkit.SslErrorHandler,%2520android.net.http.SslError)
        // WebView.getUrl() will return the top-level window URL.
        // If a top-level navigation triggers this error handler, the top-level URL will
        // be the failing URL (not the URL of the currently-rendered page).
        // This is desired behavior. We later use these values to determine whether the
        // request is a top-level navigation or a subresource request.
        String topWindowUrl = webView.getUrl();
        String failingUrl = error.getUrl();

        // Cancel request after obtaining top-level URL.
        // If request is cancelled before obtaining top-level URL, undesired behavior
        // may occur.
        // Undesired behavior: Return value of WebView.getUrl() may be the current URL
        // instead of the failing URL.
        handler.cancel();

        if (!topWindowUrl.equalsIgnoreCase(failingUrl)) {
            // If error is not due to top-level navigation, then do not call
            // onReceivedError()
            Log.w(TAG, "Resource blocked from loading due to SSL error. Blocked URL: " + failingUrl);
            return;
        }

        int code = error.getPrimaryError();
        String description = "";
        String descriptionPrefix = "SSL error: ";

        // https://developer.android.com/reference/android/net/http/SslError.html
        switch (code) {
            case SslError.SSL_DATE_INVALID:
                description = "The date of the certificate is invalid";
                break;
            case SslError.SSL_EXPIRED:
                description = "The certificate has expired";
                break;
            case SslError.SSL_IDMISMATCH:
                description = "Hostname mismatch";
                break;
            case SslError.SSL_INVALID:
                description = "A generic error occurred";
                break;
            case SslError.SSL_NOTYETVALID:
                description = "The certificate is not yet valid";
                break;
            case SslError.SSL_UNTRUSTED:
                description = "The certificate authority is not trusted";
                break;
            default:
                description = "Unknown SSL Error";
                break;
        }

        description = descriptionPrefix + description;

        this.onReceivedError(
                webView,
                code,
                description,
                failingUrl);
    }

    @Override
    public void onReceivedError(
            WebView webView,
            int errorCode,
            String description,
            String failingUrl) {

        if (ignoreErrFailedForThisURL != null
                && failingUrl.equals(ignoreErrFailedForThisURL)
                && errorCode == -1
                && description.equals("net::ERR_FAILED")) {

            // This is a workaround for a bug in the WebView.
            // See these chromium issues for more context:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=1023678
            // https://bugs.chromium.org/p/chromium/issues/detail?id=1050635
            // This entire commit should be reverted once this bug is resolved in chromium.
            setIgnoreErrFailedForThisURL(null);
            return;
        }

        super.onReceivedError(webView, errorCode, description, failingUrl);
        mLastLoadFailed = true;

        // In case of an error JS side expect to get a finish event first, and then get
        // an error event
        // Android WebView does it in the opposite way, so we need to simulate that
        // behavior
        emitFinishEvent(webView, failingUrl);

        WritableMap eventData = createWebViewEvent(webView, failingUrl);
        eventData.putDouble("code", errorCode);
        eventData.putString("description", description);

        int reactTag = RNCWebViewWrapper.getReactTagFromWebView(webView);
        UIManagerHelper.getEventDispatcherForReactTag((ReactContext) webView.getContext(), reactTag)
                .dispatchEvent(new TopLoadingErrorEvent(reactTag, eventData));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onReceivedHttpError(
            WebView webView,
            WebResourceRequest request,
            WebResourceResponse errorResponse) {
        super.onReceivedHttpError(webView, request, errorResponse);

        if (request.isForMainFrame()) {
            WritableMap eventData = createWebViewEvent(webView, request.getUrl().toString());
            eventData.putInt("statusCode", errorResponse.getStatusCode());
            eventData.putString("description", errorResponse.getReasonPhrase());

            int reactTag = RNCWebViewWrapper.getReactTagFromWebView(webView);
            UIManagerHelper.getEventDispatcherForReactTag((ReactContext) webView.getContext(), reactTag)
                    .dispatchEvent(new TopHttpErrorEvent(reactTag, eventData));
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) {
        // WebViewClient.onRenderProcessGone was added in O.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        super.onRenderProcessGone(webView, detail);

        if (detail.didCrash()) {
            Log.e(TAG, "The WebView rendering process crashed.");
        } else {
            Log.w(TAG, "The WebView rendering process was killed by the system.");
        }

        // if webView is null, we cannot return any event
        // since the view is already dead/disposed
        // still prevent the app crash by returning true.
        if (webView == null) {
            return true;
        }

        WritableMap event = createWebViewEvent(webView, webView.getUrl());
        event.putBoolean("didCrash", detail.didCrash());
        int reactTag = RNCWebViewWrapper.getReactTagFromWebView(webView);
        UIManagerHelper.getEventDispatcherForReactTag((ReactContext) webView.getContext(), reactTag)
                .dispatchEvent(new TopRenderProcessGoneEvent(reactTag, event));

        // returning false would crash the app.
        return true;
    }

    protected void emitFinishEvent(WebView webView, String url) {
        int reactTag = RNCWebViewWrapper.getReactTagFromWebView(webView);
        UIManagerHelper.getEventDispatcherForReactTag((ReactContext) webView.getContext(), reactTag)
                .dispatchEvent(new TopLoadingFinishEvent(reactTag, createWebViewEvent(webView, url)));
    }

    protected WritableMap createWebViewEvent(WebView webView, String url) {
        WritableMap event = Arguments.createMap();
        event.putDouble("target", RNCWebViewWrapper.getReactTagFromWebView(webView));
        // Don't use webView.getUrl() here, the URL isn't updated to the new value yet
        // in callbacks
        // like onPageFinished
        event.putString("url", url);
        event.putBoolean("loading", !mLastLoadFailed && webView.getProgress() != 100);
        event.putString("title", webView.getTitle());
        event.putBoolean("canGoBack", webView.canGoBack());
        event.putBoolean("canGoForward", webView.canGoForward());
        return event;
    }

    public void setProgressChangedFilter(RNCWebView.ProgressChangedFilter filter) {
        progressChangedFilter = filter;
    }
}