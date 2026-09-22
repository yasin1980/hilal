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
    private int compassAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private long lastCompassDispatchMs = 0L;
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

        // TEK KIBLE MOTORU: Android rotation-vector -> ekran koordinati -> gercek kuzey.
        // Duz/dik mod, kamera ekseni ve ikinci yon kaynagi yoktur. Telefonun ekraninin
        // ust kenari her durumda kullanicinin tuttugu yon kabul edilir.
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        switch (rotation) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
        }
        if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) return;

        float[] orientation = new float[3];
        SensorManager.getOrientation(remappedMatrix, orientation);
        float rawTrueHeading = (float)Math.toDegrees(orientation[0]);
        rawTrueHeading = (rawTrueHeading + magneticDeclination + 360f) % 360f;

        if (Float.isNaN(filteredHeading)) {
            filteredHeading = rawTrueHeading;
        } else {
            float delta = ((rawTrueHeading - filteredHeading + 540f) % 360f) - 180f;
            float ad = Math.abs(delta);

            // Sabit telefonda manyetometrenin mikro titremesini kes. Bu bir Kibleye
            // yapay kilit degildir; yalnizca sensor gurultusunu bastirir.
            if (ad < 1.25f) return;

            // One-Euro benzeri adaptif dairesel filtre: kucuk hareket sakin,
            // kullanici telefonu cevirince gecikmeden takip eder.
            float alpha = ad >= 45f ? 0.82f :
                          ad >= 20f ? 0.68f :
                          ad >= 8f  ? 0.48f :
                          ad >= 3f  ? 0.28f : 0.16f;
            filteredHeading = (filteredHeading + alpha * delta + 360f) % 360f;
        }

        // WebView'i gereksiz sensor kareleriyle bogma; 25 Hz pusula icin yeterlidir.
        long now = SystemClock.elapsedRealtime();
        if (now - lastCompassDispatchMs < 40L) return;
        lastCompassDispatchMs = now;

        final float h = filteredHeading;
        final int accuracy = compassAccuracy;
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                "window.__hilalNativeCompassActive=true;" +
                "window.__hilalNativeCompassAccuracy=" + accuracy + ";" +
                "if(typeof setHeading==='function'){setHeading(" + h + ",true);}" +
                "window.dispatchEvent(new CustomEvent('hilalNativeHeading',{detail:{heading:" + h + ",accuracy:" + accuracy + "}}));", null);
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

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor != null && sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            compassAccuracy = accuracy;
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                runOnUiThread(() -> {
                    if (webView != null) webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('hilalCompassAccuracy',{detail:{accuracy:" + accuracy + "}}));", null);
                });
            }
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
