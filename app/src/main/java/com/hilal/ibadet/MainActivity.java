package com.hilal.ibadet;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.GeomagneticField;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.net.Uri;
import android.view.Window;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.view.Surface;
import android.view.Display;

import org.json.JSONObject;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final int REQ_NOTIFICATION = 1003;
    private static final String PERMISSION_PREFS = "hilal_permission_setup_v2";

    private WebView webView;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float filteredHeading = Float.NaN;
    private PermissionRequest pendingCameraRequest;
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private volatile float magneticDeclination = 0f;
    private boolean uprightCompassMode = false;
    private boolean exactAlarmWasGranted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);
        setContentView(webView);

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

        // HTML hatırlatıcı motorunun Android AlarmManager köprüsü.
        webView.addJavascriptInterface(new HilalBridge(), "AndroidHilal");

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
        exactAlarmWasGranted = hasExactAlarmAccess();

        // Secure appassets HTTPS origin: required for reliable getUserMedia in WebView.
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        // Eski çalışan Hilâl davranışı: uygulama ilk açılışında gerekli izinleri sırayla iste.
        webView.postDelayed(this::startInitialPermissionFlow, 700L);
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
        if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        boolean exactNow = hasExactAlarmAccess();
        if (exactNow) {
            // Exact-alarm ekranından dönüldüğünde veya uygulama tekrar açıldığında
            // kalıcı tek motorun bütün kayıtlarını yeniden kur.
            ReminderScheduler.restoreAll(this);
        }
        exactAlarmWasGranted = exactNow;
    }

    private boolean hasExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Exception e) { return false; }
    }

    @Override protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int axisX = SensorManager.AXIS_X, axisY = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) { axisX = SensorManager.AXIS_Y; axisY = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { axisX = SensorManager.AXIS_MINUS_X; axisY = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { axisX = SensorManager.AXIS_MINUS_Y; axisY = SensorManager.AXIS_X; }
        if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) return;

        // Düz ve dik kullanım arasında karışım YOK. Karışım pusulanın sağa-sola
        // oynamasına neden oluyordu. Histerezis ile yalnız bir referans seçilir.
        float topEast = remappedMatrix[1], topNorth = remappedMatrix[4];
        float forwardEast = -remappedMatrix[2], forwardNorth = -remappedMatrix[5];
        float topH = (float)Math.hypot(topEast, topNorth);
        float forwardH = (float)Math.hypot(forwardEast, forwardNorth);
        if (!uprightCompassMode && forwardH > 0.78f && topH < 0.63f) uprightCompassMode = true;
        else if (uprightCompassMode && topH > 0.78f && forwardH < 0.63f) uprightCompassMode = false;

        float east = uprightCompassMode ? forwardEast : topEast;
        float north = uprightCompassMode ? forwardNorth : topNorth;
        if (Math.hypot(east, north) < 0.18) return;
        float raw = (float)Math.toDegrees(Math.atan2(east, north));
        raw = (raw + magneticDeclination + 360f) % 360f; // gerçek kuzey

        if (Float.isNaN(filteredHeading)) filteredHeading = raw;
        else {
            float delta = ((raw - filteredHeading + 540f) % 360f) - 180f;
            float ad = Math.abs(delta);

            // ODAK KİLİDİ: Telefon sabit tutulduğunda sensörün 0.5-1 derece civarındaki
            // doğal titreşimi ibreyi sağa-sola oynatmasın. Kullanıcı gerçekten döndürmeye
            // başladığında ise büyük farklarda hızlı cevap vermeye devam etsin.
            if (ad < 0.85f) return;
            float alpha = ad >= 30f ? 0.76f :
                          ad >= 12f ? 0.58f :
                          ad >= 5f  ? 0.34f :
                          ad >= 2f  ? 0.20f : 0.12f;
            filteredHeading = (filteredHeading + alpha * delta + 360f) % 360f;
        }

        final float h = filteredHeading;
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                "window.__hilalNativeCompassActive=true;" +
                "if(typeof setHeading==='function'){setHeading(" + h + ",true);}" +
                "window.dispatchEvent(new CustomEvent('hilalNativeHeading',{detail:{heading:" + h + "}}));", null);
        });
    }

    private class HilalBridge {
        @JavascriptInterface public boolean hasReminderAccess() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
            return am!=null && am.canScheduleExactAlarms();
        }

        @JavascriptInterface public void requestReminderAccess() {
            runOnUiThread(() -> requestExactAlarmAccess());
        }

        @JavascriptInterface public void scheduleReminder(String json) {
            try {
                JSONObject o = new JSONObject(json);
                if (Build.VERSION.SDK_INT >= 31 && !hasExactAlarmAccess()) {
                    // Kaydı kaybetme: Scheduler kalıcı olarak saklar ve izin dönüşünde restoreAll kurar.
                    ReminderScheduler.schedule(MainActivity.this, o, true);
                    runOnUiThread(() -> requestExactAlarmAccess());
                    return;
                }
                ReminderScheduler.schedule(MainActivity.this, o, true);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface public void cancelReminder(String id) {
            ReminderScheduler.cancel(MainActivity.this, id);
        }

        @JavascriptInterface public void cancelReminderPrefix(String prefix) {
            ReminderScheduler.cancelPrefix(MainActivity.this, prefix);
        }

        @JavascriptInterface public void setCompassLocation(double lat, double lon, double altitude) {
            try {
                GeomagneticField field = new GeomagneticField((float)lat, (float)lon, (float)altitude, System.currentTimeMillis());
                magneticDeclination = field.getDeclination();
                filteredHeading = Float.NaN;
            } catch (Exception ignored) { }
        }

        @JavascriptInterface public String consumePendingReminderId(){ return ""; }
        @JavascriptInterface public void acknowledgePendingReminderId(String id){}
        @JavascriptInterface public void stopReminderSound(){ ReminderReceiver.stopActiveSound(); }
    }

    public static boolean deliverForegroundReminder(String id, String title, String body) {
        return false;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
