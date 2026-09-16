\
package com.nsp112.hilal;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.webkit.*;
import java.util.*;

public class MainActivity extends Activity {
    private WebView webView;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(android.graphics.Color.rgb(0,63,47));
            getWindow().setNavigationBarColor(android.graphics.Color.rgb(0,63,47));
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v,url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch(Exception ignored){}
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                super.onReceivedError(v,r,e);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        HilalBridge bridge = new HilalBridge(this);
        webView.addJavascriptInterface(bridge, "AndroidHilal");
        webView.addJavascriptInterface(bridge, "HilalAndroid");
        webView.addJavascriptInterface(bridge, "Android");

        requestNotificationPermission();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 112);
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public static class HilalBridge {
        private final Activity a;
        HilalBridge(Activity a){ this.a=a; }

        @JavascriptInterface public boolean hasReminderAccess() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am=(AlarmManager)a.getSystemService(ALARM_SERVICE);
            return am.canScheduleExactAlarms();
        }

        @JavascriptInterface public void requestReminderAccess() {
            if (Build.VERSION.SDK_INT >= 31 && !hasReminderAccess()) {
                try {
                    Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:"+a.getPackageName()));
                    a.startActivity(i);
                } catch(Exception ignored){}
            }
        }

        @JavascriptInterface public void scheduleReminder(String json) {
            ReminderScheduler.schedule(a, json);
        }

        @JavascriptInterface public void cancelReminder(String id) {
            ReminderScheduler.cancel(a, id);
        }

        @JavascriptInterface public void cancelReminderPrefix(String prefix) {
            ReminderScheduler.cancelPrefix(a, prefix);
        }

        @JavascriptInterface public void exitApp() { a.finish(); }

        @JavascriptInterface public double getNotificationVolumeRatio() {
            AudioManager m=(AudioManager)a.getSystemService(AUDIO_SERVICE);
            int max=m.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
            return max<=0 ? 0 : (double)m.getStreamVolume(AudioManager.STREAM_NOTIFICATION)/max;
        }

        @JavascriptInterface public void playSystemNotificationSound() {
            try {
                Uri uri=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
                android.media.Ringtone r=android.media.RingtoneManager.getRingtone(a,uri);
                if(r!=null) r.play();
            } catch(Exception ignored){}
        }

        @JavascriptInterface public void shareContent(String title,String text,String dataUrl) {
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT,title);
            i.putExtra(Intent.EXTRA_TEXT,text);
            a.startActivity(Intent.createChooser(i,title));
        }

        @JavascriptInterface public void shareFiles(String title,String text,String payload) {
            shareContent(title,text,"");
        }
    }
}
