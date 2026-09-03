package com.storeqn.deliverydriver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.IBinder;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class LocationForegroundService extends Service implements LocationListener {
    private static final String CHANNEL_ID="yasri_tracking";
    private static final int NOTIFY_ID=1111;
    private static final String API_KEY="AIzaSyD121IwvJ1RXq-WsYof6mDCwJxco1IBDy8";
    private static final String DB="https://delivery-tracker-febcc-default-rtdb.europe-west1.firebasedatabase.app";

    private LocationManager lm;
    private String code="",name="",phone="",meta="{}";
    private String idToken="",localId="";
    private long tokenAt=0;
    private volatile boolean running=false;

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null) return START_STICKY;
        String action=intent.getAction();
        if("STOP".equals(action)){
            new Thread(this::markStoppedAndQuit).start();
            return START_NOT_STICKY;
        }
        if("META".equals(action)){
            meta=intent.getStringExtra("meta");
            if(meta==null)meta="{}";
            if(running)new Thread(this::pushMetaOnly).start();
            return START_STICKY;
        }
        if("START".equals(action)){
            code=safe(intent.getStringExtra("code"));
            name=safe(intent.getStringExtra("name"));
            phone=safe(intent.getStringExtra("phone"));
            meta=safeJson(intent.getStringExtra("meta"));
            getSharedPreferences("tracker",MODE_PRIVATE).edit()
                .putString("code",code).putString("name",name).putString("phone",phone).putString("meta",meta).apply();
            startForeground(NOTIFY_ID,notification("التتبع يعمل — يمكنك قفل الشاشة"));
            running=true;
            new Thread(() -> {
                if(authenticateAndPrepare()) startLocationUpdates();
                else updateNotification("تعذر الاتصال — افتح التطبيق وحاول مجدداً");
            }).start();
            return START_STICKY;
        }
        restore();
        if(!code.isEmpty()){
            startForeground(NOTIFY_ID,notification("استعادة تتبع الموقع..."));
            running=true;
            new Thread(() -> { if(authenticateAndPrepare()) startLocationUpdates(); }).start();
        }
        return START_STICKY;
    }

    private void restore(){
        android.content.SharedPreferences p=getSharedPreferences("tracker",MODE_PRIVATE);
        code=p.getString("code",""); name=p.getString("name",""); phone=p.getString("phone",""); meta=p.getString("meta","{}");
    }

    private boolean authenticateAndPrepare(){
        try{
            if(idToken.isEmpty() || System.currentTimeMillis()-tokenAt>45*60*1000L){
                JSONObject r=request("POST","https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="+API_KEY,
                    new JSONObject().put("returnSecureToken",true).toString(),null);
                idToken=r.getString("idToken"); localId=r.getString("localId"); tokenAt=System.currentTimeMillis();
            }
            JSONObject access=request("GET",DB+"/accessCodes/"+enc(code)+".json?auth="+enc(idToken),null,null);
            if(access==null || !"driver".equals(access.optString("role")) || !access.optBoolean("enabled",false)) return false;
            request("PUT",DB+"/sessions/"+enc(localId)+".json?auth="+enc(idToken),
                new JSONObject().put("code",code).put("createdAt",System.currentTimeMillis()).toString(),null);
            return true;
        }catch(Exception e){ return false; }
    }

    private void startLocationUpdates(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;
        try{
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,3,this);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,8000,5,this);
        }catch(Exception ignored){}
    }

    @Override public void onLocationChanged(Location l){
        if(!running)return;
        new Thread(() -> pushLocation(l)).start();
    }

    private void pushLocation(Location l){
        try{
            if(!authenticateAndPrepare()) return;
            JSONObject data;
            try{ data=new JSONObject(meta); }catch(Exception e){ data=new JSONObject(); }
            data.put("id",localId);
            data.put("code",code);
            data.put("name",name);
            data.put("phone",phone);
            data.put("lat",l.getLatitude());
            data.put("lng",l.getLongitude());
            data.put("accuracy",Math.round(l.getAccuracy()));
            data.put("speed",l.hasSpeed()?l.getSpeed():0);
            data.put("heading",l.hasBearing()?l.getBearing():0);
            data.put("online",true);
            data.put("shift",true);
            data.put("updatedAt",System.currentTimeMillis());
            request("PUT",DB+"/drivers/"+enc(localId)+".json?auth="+enc(idToken),data.toString(),null);
            updateNotification("الموقع يتحدث بالخلفية — "+name);
        }catch(Exception ignored){}
    }

    private void pushMetaOnly(){
        try{
            if(!authenticateAndPrepare())return;
            JSONObject data;
            try{data=new JSONObject(meta);}catch(Exception e){data=new JSONObject();}
            data.put("code",code); data.put("name",name); data.put("phone",phone); data.put("updatedAt",System.currentTimeMillis());
            request("PATCH",DB+"/drivers/"+enc(localId)+".json?auth="+enc(idToken),data.toString(),null);
        }catch(Exception ignored){}
    }

    private void markStoppedAndQuit(){
        running=false;
        try{ if(lm!=null)lm.removeUpdates(this); }catch(Exception ignored){}
        try{
            if(authenticateAndPrepare()){
                JSONObject d=new JSONObject().put("online",false).put("shift",false).put("updatedAt",System.currentTimeMillis());
                request("PATCH",DB+"/drivers/"+enc(localId)+".json?auth="+enc(idToken),d.toString(),null);
            }
        }catch(Exception ignored){}
        getSharedPreferences("tracker",MODE_PRIVATE).edit().clear().apply();
        stopForeground(true); stopSelf();
    }

    private JSONObject request(String method,String url,String body,String token) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(12000);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        if(body!=null){
            c.setDoOutput(true);
            try(OutputStream os=c.getOutputStream()){ os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int status=c.getResponseCode();
        InputStream is=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        String text="";
        if(is!=null){
            try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){
                String line; StringBuilder sb=new StringBuilder(); while((line=br.readLine())!=null)sb.append(line); text=sb.toString();
            }
        }
        if(status<200||status>=300) throw new IOException("HTTP "+status+" "+text);
        if(text.isEmpty()||"null".equals(text)) return null;
        return new JSONObject(text);
    }

    private String enc(String s){ try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;} }
    private String safe(String s){return s==null?"":s;}
    private String safeJson(String s){return s==null||s.isEmpty()?"{}":s;}

    private void createChannel(){
        if(android.os.Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"تتبع الدلفري",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("إشعار استمرار تتبع موقع الدلفري");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification notification(String text){
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL_ID)
            .setContentTitle("مطعم الياسري - دلفري")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text){
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFY_ID,notification(text));
    }

    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onProviderEnabled(String p){}
    @Override public void onProviderDisabled(String p){}
    @Override public void onStatusChanged(String p,int s,android.os.Bundle b){}
}
