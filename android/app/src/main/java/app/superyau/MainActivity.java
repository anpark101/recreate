package app.superyau;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.widget.EditText;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A direct online WebView: does not copy, restyle, or inject UI into the site. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://superyau.iryonmann.workers.dev/";
    private static final String HOST = "superyau.iryonmann.workers.dev";
    private static final int UPLOAD = 10, EXPORT = 11;
    private static final int MAX_BASE64 = 32 * 1024 * 1024;
    private WebView web;
    private FrameLayout root;
    private LinearLayout errorPanel;
    private ValueCallback<Uri[]> uploadCallback;
    private byte[] exportBytes;
    private boolean readingBlob;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xffffffff);
        // Keep the website clear of system bars and the keyboard on edge-to-edge Android.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        // First-party cookies and Web Storage remain enabled for the site's own settings.
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isSite(request.getUrl())) return false;
                String url = request.getUrl().toString();
                if (isSite(Uri.parse(view.getUrl() == null ? "" : view.getUrl()))
                        && (url.startsWith("blob:" + HOME) || url.startsWith("data:image/"))) return false;
                if (request.isForMainFrame() && request.hasGesture()) openExternal(request.getUrl());
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                android.webkit.CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) showError();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // Never bypass certificate validation.
                showError();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try { startActivityForResult(params.createIntent(), UPLOAD); }
                catch (ActivityNotFoundException exception) {
                    uploadCallback.onReceiveValue(null);
                    uploadCallback = null;
                    toast("No file picker is available on this device.");
                }
                return true;
            }
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                    .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mime, size) -> download(url));
        if (state == null || web.restoreState(state) == null) web.loadUrl(HOME);
    }

    private boolean isSite(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
    }

    private void openExternal(Uri uri) {
        // No intent://, file://, javascript:, or arbitrary custom scheme dispatch.
        String scheme = uri.getScheme();
        if (!"https".equals(scheme) && !"http".equals(scheme) && !"mailto".equals(scheme) && !"tel".equals(scheme)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException exception) { toast("No app can open this link."); }
    }

    private void download(String url) {
        if (!isSite(Uri.parse(web.getUrl() == null ? "" : web.getUrl()))) return;
        if (exportBytes != null || readingBlob) { toast("Finish saving the current image first."); return; }
        if (url.startsWith("data:image/")) { prepareExport(url); return; }
        if (url.startsWith("blob:" + HOME)) {
            // Read the site's blob in its existing page context; no native bridge is exposed.
            readingBlob = true;
            String script = "(function(){delete window.__superyauExport;fetch(" + JSONObject.quote(url) + ")"
                + ".then(r=>r.blob()).then(b=>{if(b.size>24000000)throw Error('large');"
                + "var r=new FileReader();r.onload=()=>{window.__superyauExport=r.result};"
                + "r.readAsDataURL(b)}).catch(()=>{window.__superyauExport='error'});return true})()";
            web.evaluateJavascript(script, result -> pollExport(0));
            return;
        }
        if (url.startsWith("https://") || url.startsWith("http://")) openExternal(Uri.parse(url));
        else toast("This download format is not supported. Try the website in Chrome.");
    }

    private void pollExport(int attempt) {
        if (isFinishing() || isDestroyed()) { readingBlob = false; return; }
        web.evaluateJavascript("window.__superyauExport || null", result -> {
            if (result != null && !result.equals("null")) {
                readingBlob = false;
                web.evaluateJavascript("delete window.__superyauExport", null);
                try {
                    String data = new org.json.JSONArray("[" + result + "]").getString(0);
                    if (data.length() > MAX_BASE64) { toast("Image is too large to save in this app."); return; }
                    prepareExport(data);
                } catch (Exception exception) { toast("Could not prepare the image. Try Chrome."); }
            } else if (attempt < 100) web.postDelayed(() -> pollExport(attempt + 1), 100);
            else { readingBlob = false; toast("Image export timed out. Please try again."); }
        });
    }

    private void prepareExport(String data) {
        if (data == null || data.length() > MAX_BASE64 || exportBytes != null) return;
        int comma = data.indexOf(',');
        if (comma < 0) { toast("Could not prepare the image. Try Chrome."); return; }
        String header = data.substring(0, comma);
        String mime, extension;
        switch (header) {
            case "data:image/png;base64": mime = "image/png"; extension = ".png"; break;
            case "data:image/jpeg;base64": mime = "image/jpeg"; extension = ".jpg"; break;
            case "data:image/webp;base64": mime = "image/webp"; extension = ".webp"; break;
            default: toast("Unsupported image format. Try Chrome."); return;
        }
        try {
            exportBytes = Base64.decode(data.substring(comma + 1), Base64.DEFAULT);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE, "superyau-" + System.currentTimeMillis() + extension);
            startActivityForResult(intent, EXPORT);
        } catch (Exception exception) { exportBytes = null; toast("Could not open the image saver."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == UPLOAD && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            uploadCallback = null;
        }
        if (request == EXPORT) {
            byte[] bytes = exportBytes;
            exportBytes = null;
            if (result != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
            Uri uri = data.getData();
            writer.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("No output stream");
                    output.write(bytes);
                    runOnUiThread(() -> toast("Image saved."));
                } catch (Exception exception) { runOnUiThread(() -> toast("Could not save the image.")); }
            });
        }
    }

    private void showError() {
        if (errorPanel != null) return;
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(android.view.Gravity.CENTER);
        errorPanel.setPadding(48, 48, 48, 48);
        errorPanel.setBackgroundColor(0xffffffff);
        TextView message = new TextView(this);
        message.setText("Unable to load Superyau.\nCheck your internet connection and try again.");
        message.setGravity(android.view.Gravity.CENTER);
        message.setTextSize(18);
        errorPanel.addView(message);
        Button retry = new Button(this);
        retry.setText("Try again");
        retry.setOnClickListener(v -> { root.removeView(errorPanel); errorPanel = null; web.loadUrl(HOME); });
        errorPanel.addView(retry);
        Button browser = new Button(this);
        browser.setText("Open in browser");
        browser.setOnClickListener(v -> openExternal(Uri.parse(HOME)));
        errorPanel.addView(browser);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) { web.saveState(state); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onPause() { web.onPause(); android.webkit.CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        web.stopLoading(); root.removeView(web); web.destroy();
        writer.shutdown();
        super.onDestroy();
    }
}
