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
import android.os.Vibrator;
import android.os.VibrationEffect;
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
    private Sensor accelerometerSensor;
    private Sensor magneticSensor;
    private final float[] accelValues = new float[3];
    private final float[] magneticValues = new float[3];
    private boolean haveAccel = false;
    private boolean haveMagnetic = false;
    private float filteredHeading = Float.NaN;
    private PermissionRequest pendingCameraRequest;
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private volatile float magneticDeclination = 0f;
    private int compassAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private long lastCompassDispatchMs = 0L;
    private boolean compassRequested = false;
    private boolean compassRegistered = false;
    private boolean exactAlarmWasGranted = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(7,61,44));
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
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
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            s.setOffscreenPreRaster(false);
        }
        webView.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

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

        // V7: acilis kritik yolunda sensor sorgulama yok. Kible acilinca hazirlanir.
        // Bu, eski cihazlarda ilk WebView cizimini sensor servisinden ayirir.
        webView.postDelayed(() -> exactAlarmWasGranted = hasExactAlarmAccess(), 1200L);

        // Secure appassets HTTPS origin: required for reliable getUserMedia in WebView.
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");

        // STABLE V2: açılışta izin ekranı zorlanmaz. İzinler ilgili özellik kullanıldığında istenir.
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
            if (webView != null) {
                webView.postDelayed(this::continueInitialPermissionFlow, 300L);
                webView.postDelayed(() -> webView.evaluateJavascript(
                        "try{window.syncEzanRemindersToNative?.();" +
                        "var b=window.AndroidHilal;if(b&&typeof b.updatePrayerStatus==='function'){" +
                        "document.dispatchEvent(new Event('visibilitychange'));}}catch(e){}", null), 700L);
            }
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
        // STABLE V3: pusula açılışta çalışmaz. Kullanıcı Kıble pusulasını başlatınca devreye girer.
        // Bu, eski cihazlarda WebView ilk çizimi ve giriş butonlarıyla sensör işinin yarışmasını önler.
        if (compassRequested) registerCompassSensors();
        boolean exactNow = hasExactAlarmAccess();
        if (exactNow && !exactAlarmWasGranted) ReminderScheduler.restoreAll(this);
        exactAlarmWasGranted = exactNow;
    }

    private void registerCompassSensors() {
        if (compassRegistered) return;
        if (sensorManager == null) {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager == null) return;
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        boolean ok = false;
        if (rotationSensor != null) {
            ok = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            boolean a = accelerometerSensor != null && sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
            boolean m = magneticSensor != null && sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
            ok = a || m;
        }
        compassRegistered = ok;
        filteredHeading = Float.NaN;
        lastCompassDispatchMs = 0L;
    }

    private void unregisterCompassSensors() {
        if (sensorManager != null && compassRegistered) sensorManager.unregisterListener(this);
        compassRegistered = false;
        haveAccel = false;
        haveMagnetic = false;
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
        unregisterCompassSensors();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        final int type = event.sensor.getType();
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        } else if (rotationSensor == null && type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelValues, 0, 3); haveAccel = true;
            if (!haveMagnetic || !SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magneticValues)) return;
        } else if (rotationSensor == null && type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticValues, 0, 3); haveMagnetic = true;
            if (!haveAccel || !SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magneticValues)) return;
        } else return;

        // STABLE V2 KIBLE: rotation-vector; yoksa accelerometer + magnetometer fallback.

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
        // STABLE V3: tek ve standart referans kullanılır: ekranın üst kenarının kuzeye göre azimutu.
        // Düz/dik mod arasında geçiş yapılmaz; bu geçiş eski sensörlerde 90/180 derece sıçramalara yol açabiliyordu.
        float rawTrueHeading = (float)Math.toDegrees(orientation[0]);
        rawTrueHeading = (rawTrueHeading + magneticDeclination + 360f) % 360f;

        if (Float.isNaN(filteredHeading)) {
            filteredHeading = rawTrueHeading;
        } else {
            float delta = ((rawTrueHeading - filteredHeading + 540f) % 360f) - 180f;
            float ad = Math.abs(delta);

            // Mikro titreşimleri tamamen yut. Küçük hareketleri özellikle yavaşlat;
            // ancak kullanıcı telefonu belirgin çevirirse gecikmeden takip et.
            if (ad < 1.6f) return;
            float alpha = ad >= 45f ? 0.68f :
                          ad >= 20f ? 0.50f :
                          ad >= 10f ? 0.32f :
                          ad >= 5f  ? 0.20f : 0.10f;
            filteredHeading = (filteredHeading + alpha * delta + 360f) % 360f;
        }

        // STABLE V3: eski WebView ana iş parçacığını sensör olaylarıyla boğma; 10 Hz yeterlidir.
        long now = SystemClock.elapsedRealtime();
        if (now - lastCompassDispatchMs < 200L) return;
        lastCompassDispatchMs = now;

        final float h = filteredHeading;
        final int accuracy = compassAccuracy;
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                "window.__hilalNativeCompassActive=true;" +
                "window.__hilalNativeCompassAccuracy=" + accuracy + ";" +
                "window.dispatchEvent(new CustomEvent('hilalNativeHeading',{detail:{heading:" + h + ",accuracy:" + accuracy + "}}));", null);
        });
    }

    private class HilalBridge {
        @JavascriptInterface public void performHaptic(int kind) {
            runOnUiThread(() -> {
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v == null || !v.hasVibrator()) return;
                    long ms = kind == 1 ? 32L : 22L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(ms);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void startCompass() {
            runOnUiThread(() -> {
                compassRequested = true;
                registerCompassSensors();
            });
        }

        @JavascriptInterface public void stopCompass() {
            runOnUiThread(() -> {
                compassRequested = false;
                unregisterCompassSensors();
                if (webView != null) webView.evaluateJavascript("window.__hilalNativeCompassActive=false;", null);
            });
        }

        @JavascriptInterface public boolean hasReminderAccess() {
            boolean notificationOk = Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            return notificationOk && hasExactAlarmAccess();
        }

        @JavascriptInterface public void requestReminderAccess() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                    return;
                }
                requestExactAlarmAccess();
            });
        }

        @JavascriptInterface public void updatePrayerStatus(String json) {
            try {
                if (json == null || json.trim().isEmpty()) return;
                // Web tarafındaki güncel vakitleri native kalıcı bildirim motoruna aktar.
                getSharedPreferences("hilal_prayer_status_v1", MODE_PRIVATE)
                        .edit().putString("times", json).apply();
                if (Build.VERSION.SDK_INT < 33 ||
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    PrayerStatusScheduler.showNow(MainActivity.this);
                }
            } catch (Exception ignored) { }
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
        if (sensor != null && (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR ||
                (rotationSensor == null && sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD))) {
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
