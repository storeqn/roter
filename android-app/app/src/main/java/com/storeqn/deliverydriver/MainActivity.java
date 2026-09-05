package com.storeqn.deliverydriver;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 1001;
    private static final String START_URL = "https://storeqn.github.io/roter/driver.html";
    private WebView webView;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    }, LOCATION_REQUEST);
                }
            }
        });

        if (savedInstanceState == null) webView.loadUrl(START_URL);
        else webView.restoreState(savedInstanceState);
    }

    private String buildStableDeviceId() {
        try {
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.trim().isEmpty()) return "";
            String source = androidId + "|" + getPackageName();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return "android-" + out.substring(0, 32);
        } catch (Exception e) {
            return "";
        }
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context){ this.context=context; }

        @JavascriptInterface
        public boolean isNativeBackgroundTrackingAvailable(){ return true; }

        @JavascriptInterface
        public boolean isDriverApp(){ return true; }

        @JavascriptInterface
        public String getDeviceId(){ return buildStableDeviceId(); }

        @JavascriptInterface
        public void startTracking(String code, String name, String phone, String metaJson){
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
                    return;
                }
                Intent i=new Intent(context, LocationForegroundService.class);
                i.setAction("START");
                i.putExtra("code",code);
                i.putExtra("name",name);
                i.putExtra("phone",phone);
                i.putExtra("meta",metaJson);
                startForegroundService(i);
            });
        }

        @JavascriptInterface
        public void updateMeta(String metaJson){
            Intent i=new Intent(context, LocationForegroundService.class);
            i.setAction("META");
            i.putExtra("meta",metaJson);
            startService(i);
        }

        @JavascriptInterface
        public void stopTracking(){
            Intent i=new Intent(context, LocationForegroundService.class);
            i.setAction("STOP");
            startService(i);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==LOCATION_REQUEST && pendingGeoCallback!=null){
            boolean granted=grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED;
            pendingGeoCallback.invoke(pendingGeoOrigin,granted,false);
            pendingGeoCallback=null; pendingGeoOrigin=null;
        }
    }

    @Override
    public void onBackPressed(){
        if(webView!=null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState){
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
}
