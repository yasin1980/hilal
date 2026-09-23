package com.hilal.ibadet;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewOutcomeReceiver;
import androidx.webkit.WebViewStartUpConfig;
import androidx.webkit.WebViewStartUpResult;
import androidx.webkit.WebViewStartupException;
import java.util.concurrent.Executors;

/** HILAL STABLE V5: WebView'in agir ilk baslatma isini Activity'den once arka planda baslatir. */
public final class HilalApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            WebViewStartUpConfig config = new WebViewStartUpConfig.Builder(Executors.newSingleThreadExecutor())
                    .setShouldRunUiThreadStartUpTasks(false)
                    .build();
            WebViewCompat.startUpWebView(getApplicationContext(), config,
                    new WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException>() {
                        @Override public void onResult(@NonNull WebViewStartUpResult result) {
                            Log.i("HilalStartup", "WebView background startup ready");
                        }
                        @Override public void onError(@NonNull WebViewStartupException error) {
                            Log.w("HilalStartup", "WebView async startup fallback", error);
                        }
                    });
        } catch (Throwable t) {
            Log.w("HilalStartup", "WebView startup API unavailable; normal startup will be used", t);
        }
    }
}
