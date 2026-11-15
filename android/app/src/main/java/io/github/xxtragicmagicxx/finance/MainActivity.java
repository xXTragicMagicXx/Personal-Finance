package io.github.xxtragicmagicxx.finance;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A thin shell around the Finance web app (BuildConfig.SITE_URL). The page does all the work;
 * this class supplies what a plain WebView lacks: the monthly reminder notification, a file
 * picker for importing, saving and sharing exported files (via the AndroidApp JavaScript object),
 * and opening outside links in other apps.
 */
public class MainActivity extends Activity {
    static final String EXTRA_PAGE = "page";
    private static final int RC_FILE = 1, RC_NOTIFY = 2;
    private static final String SITE = BuildConfig.SITE_URL;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private boolean testAfterAllow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cleanOutDir();
        Reminder.channel(this);
        Reminder.schedule(this);
        web = new WebView(this);
        setContentView(web);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(s.getUserAgentString() + " FinanceApp/" + BuildConfig.VERSION_NAME);
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        String page = getIntent().getStringExtra(EXTRA_PAGE);
        if (state == null || web.restoreState(state) == null) web.loadUrl(SITE + (page != null ? "#" + page : ""));
        else if (page != null) goPage(page);

        // First start: the reminder is on by default, so ask for permission to show it (Android 13+).
        if (!Reminder.prefs(this).getBoolean("asked", false)) {
            Reminder.prefs(this).edit().putBoolean("asked", true).apply();
            if (Reminder.enabled(this)) askNotify();
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String page = i.getStringExtra(EXTRA_PAGE);
        if (page != null) goPage(page);
    }

    private void goPage(String page) {
        web.evaluateJavascript("window.goPage&&goPage(" + org.json.JSONObject.quote(page) + ")", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    /** Back closes the open sheet inside the app first (the page tracks those as history steps). */
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private boolean needsNotifyPermission() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /** Asks for notification permission; false when it is already granted (or not needed). */
    private boolean askNotify() {
        if (!needsNotifyPermission()) return false;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIFY);
        return true;
    }

    private File outDir() {
        File d = new File(getCacheDir(), "out");
        d.mkdirs();
        return d;
    }

    /** Shared files are only needed briefly; drop ones older than a day. */
    private void cleanOutDir() {
        File[] fs = outDir().listFiles();
        if (fs == null) return;
        long old = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : fs) if (f.lastModified() < old) f.delete();
    }

    private Uri share(File f) {
        return FileProvider.getUriForFile(this, getPackageName() + ".files", f);
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }

    private boolean isSite(Uri u) {
        String h = Uri.parse(SITE).getHost();
        return h != null && h.equalsIgnoreCase(u.getHost());
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            if (isSite(u)) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (ActivityNotFoundException e) {
                toast("No app can open this link.");
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (!r.isForMainFrame()) return;
            // Only reached before the first successful load; after that the page works offline.
            String html = "<meta name=viewport content='width=device-width,initial-scale=1'>"
                    + "<body style='font:17px system-ui,sans-serif;padding:40px 24px;text-align:center'>"
                    + "<h2>Can't reach Finance</h2><p>The first start needs an internet connection. "
                    + "After that it works offline.</p><p><button style='font:inherit;padding:12px 24px' "
                    + "onclick=\"location.replace('" + SITE + "')\">Try again</button></p>";
            v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            // Any file: file managers label CSV and JSON backups inconsistently.
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(pick, "Choose a file"), RC_FILE);
            } catch (ActivityNotFoundException e) {
                fileCb = null;
                cb.onReceiveValue(null);
                toast("No app can pick files.");
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int rc, int result, Intent data) {
        super.onActivityResult(rc, result, data);
        if (rc != RC_FILE || fileCb == null) return;
        Uri[] out = null;
        if (result == RESULT_OK && data != null && data.getData() != null) out = new Uri[]{data.getData()};
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] results) {
        super.onRequestPermissionsResult(rc, perms, results);
        if (rc != RC_NOTIFY) return;
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (ok && testAfterAllow) Reminder.show(this);
        testAfterAllow = false;
        web.evaluateJavascript("window.onReminder&&onReminder()", null);
    }

    /**
     * window.AndroidApp in the page. A file is sent as begin(name), append(base64 chunk)..., then
     * share(...) or save(...). These run on a WebView worker thread, so anything touching the UI
     * hops to the main thread.
     */
    private class Bridge {
        private File cur;
        private FileOutputStream os;

        @JavascriptInterface
        public String version() {
            return BuildConfig.VERSION_NAME;
        }

        /** The reminder's settings as JSON: on, hour, allowed (notifications permitted), next (ms). */
        @JavascriptInterface
        public String reminder() {
            Activity a = MainActivity.this;
            return "{\"on\":" + Reminder.enabled(a) + ",\"hour\":" + Reminder.hour(a)
                    + ",\"allowed\":" + Reminder.allowed(a) + ",\"next\":" + Reminder.next(a) + "}";
        }

        @JavascriptInterface
        public void setReminder(boolean on, int hour) {
            Reminder.set(MainActivity.this, on, hour);
            if (on) runOnUiThread(MainActivity.this::askNotify);
        }

        /** Shows the reminder now, asking for permission first if needed. */
        @JavascriptInterface
        public void testReminder() {
            runOnUiThread(() -> {
                if (askNotify()) testAfterAllow = true;
                else if (!Reminder.allowed(MainActivity.this)) openNotifySettings();
                else Reminder.show(MainActivity.this);
            });
        }

        /** Opens the system page where notifications for this app are switched on. */
        @JavascriptInterface
        public void openNotifySettings() {
            runOnUiThread(() -> {
                try {
                    Intent i;
                    if (Build.VERSION.SDK_INT >= 26) {
                        i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                    }
                    startActivity(i);
                } catch (Exception e) {
                    toast("Open Settings > Apps > Finance > Notifications to allow reminders.");
                }
            });
        }

        @JavascriptInterface
        public synchronized boolean begin(String name) {
            try {
                if (os != null) os.close();
                String safe = name == null ? "file" : name.replaceAll("[^A-Za-z0-9._ -]", "_");
                cur = new File(outDir(), safe);
                os = new FileOutputStream(cur);
                return true;
            } catch (Exception e) {
                os = null;
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean append(String b64) {
            try {
                os.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private File finish() {
            try {
                if (os != null) os.close();
            } catch (Exception ignored) {
            }
            os = null;
            return cur;
        }

        /** Opens the Android share sheet with the file attached. */
        @JavascriptInterface
        public synchronized void share(String mime, String subject) {
            final File f = finish();
            if (f == null) return;
            runOnUiThread(() -> {
                try {
                    Uri u = MainActivity.this.share(f);
                    Intent i = new Intent(Intent.ACTION_SEND).setType(mime == null || mime.isEmpty() ? "*/*" : mime);
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    if (subject != null && !subject.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, subject);
                    i.setClipData(ClipData.newRawUri(f.getName(), u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Send " + f.getName()));
                } catch (Exception e) {
                    toast("Could not share the file.");
                }
            });
        }

        /** Copies the file into the phone's Downloads folder (Android 10+); older phones get the share sheet. */
        @JavascriptInterface
        public synchronized void save(String mime) {
            final File f = finish();
            if (f == null) return;
            if (Build.VERSION.SDK_INT < 29) {
                cur = f;
                share(mime, "");
                return;
            }
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(u)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                toast("Saved to Downloads: " + f.getName());
            } catch (Exception e) {
                toast("Could not save the file. Try Share instead.");
            }
        }

        /** Tints the system bars to match the page's theme (top = page background, bottom = tab bar). */
        @JavascriptInterface
        public void bars(final String top, final String bottom, final boolean dark) {
            runOnUiThread(() -> {
                try {
                    Window w = getWindow();
                    w.setStatusBarColor(Color.parseColor(top));
                    w.setNavigationBarColor(Color.parseColor(bottom));
                    View d = w.getDecorView();
                    int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
                    int f = d.getSystemUiVisibility();
                    d.setSystemUiVisibility(dark ? f & ~light : f | light);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
