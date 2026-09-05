package com.hilal.ibadet;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.Context;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity {
    private static WeakReference<MainActivity> foregroundActivity = new WeakReference<>(null);
    private WebView webView;
    private String pendingReminderId = "";
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_PICKER_REQUEST = 9001;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final String PERMISSION_PREFS = "hilal_permission_setup_v1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingReminderId = getIntent() == null ? "" :
                getIntent().getStringExtra("hilalReminderId");
        if (pendingReminderId == null) pendingReminderId = "";

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        getWindow().setStatusBarColor(0xFF061A14);
        getWindow().setNavigationBarColor(0xFF061A14);

        webView = new WebView(this);
        webView.setFitsSystemWindows(true);
        webView.setHapticFeedbackEnabled(true);
        HilalAndroidBridge bridge = new HilalAndroidBridge();
        webView.addJavascriptInterface(bridge, "HilalAndroid");
        webView.addJavascriptInterface(bridge, "AndroidHilal");
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            private boolean handleExternal(Uri uri) {
                if (uri == null) return true;
                String value = uri.toString();
                if (value.startsWith("file:///android_asset/") || value.startsWith("about:blank") ||
                        value.startsWith("blob:") || value.startsWith("data:")) return false;
                try {
                    Intent external = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(external);
                } catch (Exception ignored) { }
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternal(request == null ? null : request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternal(url == null ? null : Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Büyük yerel HTML tamamen hazır olmadan bildirim hedefini tüketme.
                view.postDelayed(() -> dispatchReminderTarget(getIntent()), 250L);
                view.postDelayed(() -> dispatchReminderTarget(getIntent()), 1200L);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    String[] accepted = params.getAcceptTypes();
                    String requestedType = "*/*";
                    ArrayList<String> acceptedTypes = new ArrayList<>();
                    if (accepted != null) {
                        for (String group : accepted) {
                            if (group == null) continue;
                            for (String rawType : group.split(",")) {
                                String type = rawType.trim();
                                if (type.isEmpty() || type.startsWith(".")) continue;
                                if (!acceptedTypes.contains(type)) acceptedTypes.add(type);
                            }
                        }
                    }
                    if (acceptedTypes.size() == 1) requestedType = acceptedTypes.get(0);
                    else if (!acceptedTypes.isEmpty())
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptedTypes.toArray(new String[0]));
                    intent.setType(requestedType);
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE)
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, FILE_PICKER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileCallback = null;
                    return false;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                            GeolocationPermissions.Callback callback) {
                boolean granted = checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (!granted) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
                boolean trustedOrigin = origin != null && origin.startsWith("file://");
                callback.invoke(origin, granted && trustedOrigin, false);
            }
        });

        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(savedInstanceState);

        webView.postDelayed(this::startInitialPermissionFlow, 700L);
    }

    private void startInitialPermissionFlow() {
        if (getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                .getBoolean("initial_flow_completed", false)) return;
        continueInitialPermissionFlow();
    }

    private void continueInitialPermissionFlow() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                .getBoolean("notification_asked", false)) {
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("notification_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                !getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                .getBoolean("location_asked", false)) {
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("location_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        requestExactAlarmAccess(false);
        getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                .putBoolean("initial_flow_completed", true).apply();
    }

    private void requestExactAlarmAccess(boolean force) {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarm == null || alarm.canScheduleExactAlarms()) return;
        if (!force && getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                .getBoolean("exact_screen_opened", false)) return;
        try {
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("exact_screen_opened", true).apply();
            Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settingsIntent);
        } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST || requestCode == LOCATION_PERMISSION_REQUEST) {
            webView.postDelayed(this::continueInitialPermissionFlow, 300L);
        }
    }

    public class HilalAndroidBridge {
        @JavascriptInterface
        public double getNotificationVolumeRatio() {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return 0.0;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
            int current = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
            return max > 0 ? (double) current / (double) max : 0.0;
        }

        @JavascriptInterface
        public void performHaptic(int kind) {
            runOnUiThread(() -> {
                if (webView == null) return;
                int feedback = kind > 0
                        ? HapticFeedbackConstants.LONG_PRESS
                        : HapticFeedbackConstants.CLOCK_TICK;
                webView.performHapticFeedback(feedback);
            });
        }

        @JavascriptInterface
        public synchronized String consumePendingReminderId() {
            // Geriye uyumlu isim; hedef yalnızca JS gerçekten açtığını bildirdiğinde silinir.
            return pendingReminderId == null ? "" : pendingReminderId;
        }

        @JavascriptInterface
        public synchronized void acknowledgePendingReminderId(String id) {
            if (id != null && id.equals(pendingReminderId)) pendingReminderId = "";
        }

        @JavascriptInterface
        public void scheduleReminder(String json) {
            try {
                JSONObject data = new JSONObject(json);
                String id = data.optString("id", "hilal-reminder");
                long whenMs = data.optLong("whenMs", 0L);
                if (whenMs <= System.currentTimeMillis()) return;
                String soundData = data.optString("soundData", "");
                File audioFile = new File(getFilesDir(), "reminder_" + id.hashCode() + ".audio");
                if (soundData.startsWith("data:audio/")) {
                    int comma = soundData.indexOf(',');
                    if (comma > 0) {
                        byte[] bytes = Base64.decode(soundData.substring(comma + 1), Base64.DEFAULT);
                        try (FileOutputStream output = new FileOutputStream(audioFile)) {
                            output.write(bytes);
                        }
                        data.put("soundPath", audioFile.getAbsolutePath());
                    }
                } else if (!data.optString("soundUrl", "").isEmpty()) {
                    String soundUrl = data.optString("soundUrl", "");
                    data.put("soundPath", audioFile.getAbsolutePath());
                    new Thread(() -> {
                        try (InputStream input = new URL(soundUrl).openStream();
                             FileOutputStream output = new FileOutputStream(audioFile)) {
                            byte[] buffer = new byte[8192]; int count;
                            while ((count = input.read(buffer)) > 0) output.write(buffer, 0, count);
                        } catch (Exception ignored) { }
                        data.remove("soundData");
                        data.remove("soundUrl");
                        ReminderScheduler.schedule(MainActivity.this, data, true);
                    }).start();
                    return;
                }
                data.remove("soundData");
                data.remove("soundUrl");
                ReminderScheduler.schedule(MainActivity.this, data, true);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void updatePrayerStatus(String json) {
            try {
                PrayerStatusScheduler.update(MainActivity.this, json);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void cancelReminder(String id) {
            ReminderScheduler.cancel(MainActivity.this, id);
        }

        @JavascriptInterface
        public void cancelReminderPrefix(String prefix) {
            ReminderScheduler.cancelPrefix(MainActivity.this, prefix == null ? "" : prefix);
        }

        @JavascriptInterface
        public boolean hasReminderAccess() {
            boolean notifications = Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            boolean exact = Build.VERSION.SDK_INT < 31 || alarm == null || alarm.canScheduleExactAlarms();
            return notifications && exact;
        }

        @JavascriptInterface
        public void requestReminderAccess() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    boolean asked = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                            .getBoolean("notification_asked", false);
                    if (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                                .putBoolean("notification_asked", true).apply();
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_PERMISSION_REQUEST);
                    } else {
                        try {
                            Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                            settingsIntent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                            startActivity(settingsIntent);
                        } catch (Exception ignored) { }
                    }
                    return;
                }
                requestExactAlarmAccess(true);
            });
        }

        @JavascriptInterface
        public void shareContent(String title, String text, String imageDataUrl) {
            try {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Hilâl" : title);
                share.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                boolean hasImage = imageDataUrl != null && imageDataUrl.startsWith("data:image/");
                if (hasImage) {
                    int comma = imageDataUrl.indexOf(',');
                    byte[] bytes = Base64.decode(imageDataUrl.substring(comma + 1), Base64.DEFAULT);
                    File shareDir = new File(getCacheDir(), "shared_virds");
                    if (!shareDir.exists()) shareDir.mkdirs();
                    File[] oldFiles = shareDir.listFiles();
                    if (oldFiles != null) for (File oldFile : oldFiles) oldFile.delete();
                    File outputFile = new File(shareDir, "hilal_paylasim.png");
                    try (FileOutputStream output = new FileOutputStream(outputFile)) { output.write(bytes); }
                    Uri uri = HilalShareProvider.uriFor(MainActivity.this, outputFile);
                    share.setType("image/png");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.setClipData(ClipData.newRawUri("Hilâl paylaşım kartı", uri));
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else share.setType("text/plain");
                runOnUiThread(() -> startActivity(Intent.createChooser(share, "Hilâl paylaşımını gönder")));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Intent fallback = new Intent(Intent.ACTION_SEND);
                    fallback.setType("text/plain");
                    fallback.putExtra(Intent.EXTRA_SUBJECT, title == null ? "Hilâl" : title);
                    fallback.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                    startActivity(Intent.createChooser(fallback, "Hilâl paylaşımını gönder"));
                });
            }
        }

        @JavascriptInterface
        public void shareFiles(String title, String text, String filesJson) {
            try {
                JSONArray files = new JSONArray(filesJson == null ? "[]" : filesJson);
                if (files.length() > 5) throw new IllegalArgumentException("Çok fazla paylaşım dosyası");
                ArrayList<Uri> uris = new ArrayList<>();
                ArrayList<String> mimeTypes = new ArrayList<>();
                long totalBytes = 0L;
                File shareDir = new File(getCacheDir(), "shared_virds");
                if (!shareDir.exists()) shareDir.mkdirs();
                File[] oldFiles = shareDir.listFiles();
                if (oldFiles != null) for (File oldFile : oldFiles) oldFile.delete();

                for (int i = 0; i < files.length(); i++) {
 
