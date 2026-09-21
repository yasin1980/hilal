package com.hilal.ibadet;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.view.Window;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;
import java.lang.ref.WeakReference;

public class MainActivity extends Activity implements SensorEventListener {
    private static WeakReference<MainActivity> foregroundActivity = new WeakReference<>(null);
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final int REQ_NOTIFICATION = 1003;
    private static final String PERMISSION_PREFS = "hilal_permission_setup_v2";

    private WebView webView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float filteredHeading = Float.NaN;
    private PermissionRequest pendingCameraRequest;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);
        HilalReminderBridge reminderBridge = new HilalReminderBridge();
        webView.addJavascriptInterface(reminderBridge, "HilalAndroid");
        webView.addJavascriptInterface(reminderBridge, "AndroidHilal");
        setContentView(webView);

        try {
            ReminderReceiver.stopActiveSound();
            PrayerStatusScheduler.scheduleNext(this, 1200L);
        } catch (Exception ignored) { }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                    callback.invoke(origin, true, false);
                }
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsCamera = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            wantsCamera = true;
                            break;
                        }
                    }
                    if (!wantsCamera) {
                        request.deny();
                        return;
                    }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Secure appassets HTTPS origin: required for reliable getUserMedia in WebView.
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        // Eski çalışan Hilâl davranışı: uygulama ilk açılışında gerekli izinleri sırayla iste.
        webView.postDelayed(this::startInitialPermissionFlow, 700L);
    }



    public class HilalReminderBridge {
        @JavascriptInterface
        public void scheduleReminder(String json) {
            try {
                JSONObject data = new JSONObject(json);
                if (data.optLong("whenMs", 0L) <= System.currentTimeMillis()) return;
                ReminderScheduler.schedule(MainActivity.this, data, true);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void cancelReminder(String id) {
            if (id != null) ReminderScheduler.cancel(MainActivity.this, id);
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
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                    return;
                }
                requestExactAlarmAccess();
            });
        }

        @JavascriptInterface
        public void stopReminderSound() {
            ReminderReceiver.stopActiveSound();
        }
    }

    private void startInitialPermissionFlow() {
        continueInitialPermissionFlow();
    }

    private void continueInitialPermissionFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }

        requestExactAlarmAccess();
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarm == null || alarm.canScheduleExactAlarms()) return;

            boolean opened = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                    .getBoolean("exact_screen_opened", false);
            if (opened) return;

            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean("exact_screen_opened", true).apply();

            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) { }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION || requestCode == REQ_LOCATION) {
            if (webView != null) webView.postDelayed(this::continueInitialPermissionFlow, 300L);
        }

        if (requestCode == REQ_CAMERA && pendingCameraRequest != null) {
            PermissionRequest request = pendingCameraRequest;
            pendingCameraRequest = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                request.deny();
                if (webView != null) {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('hilalCameraDenied'));", null);
                }
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foregroundActivity = new WeakReference<>(this);
        ReminderScheduler.restoreAll(this);
        if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        if (webView != null) {
            webView.postDelayed(() -> webView.evaluateJavascript(
                    "try{syncAllRemindersToNative&&syncAllRemindersToNative();syncEzanRemindersToNative&&syncEzanRemindersToNative();syncVirtRemindersToNative&&syncVirtRemindersToNative()}catch(e){}",
                    null), 900L);
        }
    }

    @Override protected void onPause() {
        MainActivity active = foregroundActivity.get();
        if (active == this) foregroundActivity.clear();
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] rotation = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        SensorManager.getOrientation(rotation, orientation);
        float raw = (float)Math.toDegrees(orientation[0]);
        if (raw < 0) raw += 360f;

        if (Float.isNaN(filteredHeading)) filteredHeading = raw;
        float delta = ((raw - filteredHeading + 540f) % 360f) - 180f;
        if (Math.abs(delta) < 0.25f) return;
        // Hızlı tepki + küçük sensör titreşimlerinde kontrollü yumuşatma.
        // Büyük dönüşlerde ibre telefonu gecikmeden yakalar.
        float absDelta = Math.abs(delta);
        float alpha = absDelta > 35f ? 0.72f : (absDelta > 10f ? 0.52f : 0.34f);
        filteredHeading = (filteredHeading + alpha * delta + 360f) % 360f;

        final float h = filteredHeading;
        runOnUiThread(() -> webView.evaluateJavascript(
            "if(typeof setHeading==='function'){setHeading(" + h + ");}" +
            "window.dispatchEvent(new CustomEvent('hilalNativeHeading',{detail:{heading:" + h + "}}));", null));
    }

    public static boolean deliverForegroundReminder(String id, String title, String body) {
        MainActivity activity = foregroundActivity.get();
        if (activity == null || activity.webView == null || activity.isFinishing()) return false;
        PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if ((power != null && !power.isInteractive()) ||
                (keyguard != null && keyguard.isKeyguardLocked())) return false;
        activity.runOnUiThread(() -> activity.webView.evaluateJavascript(
                "try{window.hilalShowForegroundReminder&&window.hilalShowForegroundReminder(" +
                        JSONObject.quote(id == null ? "" : id) + "," +
                        JSONObject.quote(title == null ? "Hilâl Hatırlatıcı" : title) + "," +
                        JSONObject.quote(body == null ? "Hatırlatma zamanı" : body) + ")}catch(e){}", null));
        return true;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
