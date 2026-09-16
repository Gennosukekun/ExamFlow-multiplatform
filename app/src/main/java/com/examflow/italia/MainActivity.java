package com.examflow.italia;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Calendar;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String BASE = "https://examflow-4zkm.hatchable.site";
    private static final String AUTH_START = BASE + "/api/auth/login/start";
    private static final String AUTH_VERIFY = BASE + "/api/auth/login/verify-code";
    private static final String AUTH_SESSION = BASE + "/api/auth/get-session";
    private static final String AUTH_SIGNOUT = BASE + "/api/auth/sign-out";
    private static final String STATE = BASE + "/api/account/state";
    private static final String QUIZ = BASE + "/api/generate-quiz";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this); setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setDatabaseEnabled(true);
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient()); webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("window.examflowBack ? window.examflowBack() : false", value -> {
            if ("true".equals(value)) return; if (webView.canGoBack()) webView.goBack(); else MainActivity.super.onBackPressed();
        });
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return ""; BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); return sb.toString();
    }

    private void captureCookies(HttpURLConnection c) {
        try { Map<String,List<String>> fields=c.getHeaderFields(); for(Map.Entry<String,List<String>> e:fields.entrySet()) if(e.getKey()!=null&&"Set-Cookie".equalsIgnoreCase(e.getKey())) for(String cookie:e.getValue()) CookieManager.getInstance().setCookie(BASE,cookie); CookieManager.getInstance().flush(); } catch(Exception ignored){}
    }

    private String request(String url,String method,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(20000); c.setReadTimeout(45000); c.setRequestMethod(method); c.setRequestProperty("Accept","application/json");
        String cookie=CookieManager.getInstance().getCookie(BASE); if(cookie!=null&&!cookie.isEmpty()) c.setRequestProperty("Cookie",cookie);
        if(body!=null){ c.setRequestProperty("Content-Type","application/json; charset=utf-8"); c.setDoOutput(true); try(OutputStream os=c.getOutputStream()){os.write(body.getBytes(StandardCharsets.UTF_8));}}
        int code=c.getResponseCode(); captureCookies(c); String text=readStream(code>=200&&code<300?c.getInputStream():c.getErrorStream());
        JSONObject out=new JSONObject(); out.put("status",code); out.put("ok",code>=200&&code<300);
        if(text==null||text.isEmpty()) out.put("data",new JSONObject()); else { try{out.put("data",new JSONObject(text));}catch(Exception ex){out.put("raw",text);} }
        return out.toString();
    }

    private void callback(String callbackId,boolean ok,String data){ runOnUiThread(()->webView.evaluateJavascript("nativeCallback("+JSONObject.quote(callbackId)+","+ok+","+JSONObject.quote(data)+")",null)); }

    public class AndroidBridge {
        private void asyncRequest(String url,String method,String body,String callbackId){ new Thread(()->{ try{callback(callbackId,true,request(url,method,body));}catch(Exception e){try{callback(callbackId,false,new JSONObject().put("status",0).put("ok",false).put("error",e.getMessage()==null?"Errore di rete":e.getMessage()).toString());}catch(Exception ignored){callback(callbackId,false,"{\"ok\":false,\"error\":\"Errore di rete\"}");}}}).start(); }
        @JavascriptInterface public void authStatus(String callbackId){asyncRequest(AUTH_SESSION,"GET",null,callbackId);}
        @JavascriptInterface public void startLogin(String email,String callbackId){try{asyncRequest(AUTH_START,"POST",new JSONObject().put("email",email).toString(),callbackId);}catch(Exception e){callback(callbackId,false,"{\"ok\":false,\"error\":\"Email non valida\"}");}}
        @JavascriptInterface public void verifyCode(String email,String code,String callbackId){try{asyncRequest(AUTH_VERIFY,"POST",new JSONObject().put("email",email).put("code",code).toString(),callbackId);}catch(Exception e){callback(callbackId,false,"{\"ok\":false,\"error\":\"Codice non valido\"}");}}
        @JavascriptInterface public void signOut(String callbackId){new Thread(()->{try{String result=request(AUTH_SIGNOUT,"POST","{}");CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();callback(callbackId,true,result);}catch(Exception e){CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();callback(callbackId,true,"{\"ok\":true}");}}).start();}
        @JavascriptInterface public void loadCloud(String callbackId){asyncRequest(STATE,"GET",null,callbackId);}
        @JavascriptInterface public void saveCloud(String stateJson,String callbackId){try{JSONObject payload=new JSONObject().put("state",new JSONObject(stateJson));asyncRequest(STATE,"PUT",payload.toString(),callbackId);}catch(Exception e){callback(callbackId,false,"{\"ok\":false,\"error\":\"Dati non validi\"}");}}
        @JavascriptInterface public void setStudyReminders(boolean enabled){try{Intent intent=new Intent(MainActivity.this,StudyReminderReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(MainActivity.this,9001,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(Context.ALARM_SERVICE);if(!enabled){am.cancel(pi);return;}Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,19);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);if(cal.getTimeInMillis()<=System.currentTimeMillis())cal.add(Calendar.DAY_OF_YEAR,1);am.setInexactRepeating(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pi);}catch(Exception ignored){}}
        @JavascriptInterface public void generateQuiz(String payload,String callbackId){asyncRequest(QUIZ,"POST",payload,callbackId);}
    }
}
