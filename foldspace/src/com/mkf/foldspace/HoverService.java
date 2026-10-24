package com.mkf.foldspace;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;

/** User-started foreground monitor. It displays its own overlay, never a hidden activity. */
public final class HoverService extends Service {
    private static HoverService instance;
    private static final String STOP="com.mkf.foldspace.STOP";
    private FoldMonitor monitor;
    private WindowManager windows;
    private SpacePanel overlay;
    private ForegroundAppProbe entryProbe;
    private String lastAutoPackage="";
    private int lastSourceTask=-1;
    private boolean suppressed, lastHover, receiverRegistered;
    private long lastWrite;
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){hide();}
            else if(Intent.ACTION_USER_PRESENT.equals(i.getAction()) && monitor.hovering() && !suppressed) beginEntry();
        }
    };
    public static void suppressCurrent(){if(instance!=null){instance.suppressed=true;instance.hide();}}
    @Override public void onCreate(){
        super.onCreate(); instance=this; windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("hinge","自动悬停监测",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,HoverService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,"hinge").setSmallIcon(R.drawable.ic_space).setContentTitle("折境空间 · 自动悬停")
            .setContentText("半展开时显示应用目录 · 点击管理").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止监测",stop).build()).build();
        startForeground(7,note);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screen,filter);
        receiverRegistered=true;
        monitor=new FoldMonitor(this,(angle,hover,available)->{
            boolean changed=hover!=lastHover;
            if(!hover){suppressed=false;hide();}else if(changed && !suppressed) beginEntry();
            lastHover=hover;
            if(overlay!=null) overlay.status(angle,hover,available);
            diagnostics(angle,hover,available,changed);
        });
        monitor.start();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && STOP.equals(intent.getAction())){AppCatalog.prefs(this).edit().putBoolean("auto",false).apply();stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private boolean mayShow(){return overlay==null && !MainActivity.visible && !suppressed && monitor.hovering() && Settings.canDrawOverlays(this)
        && !getSystemService(KeyguardManager.class).isKeyguardLocked() && getSystemService(PowerManager.class).isInteractive();}
    private void beginEntry(){
        if(!mayShow() || entryProbe!=null)return;
        ForegroundAppProbe attempt=new ForegroundAppProbe(this);entryProbe=attempt;
        attempt.start(app->{if(entryProbe!=attempt)return;entryProbe=null;if(mayShow())show(app);});
    }
    private void show(AppCatalog.App running){
        if(!mayShow())return;
        SpacePanel next=new SpacePanel(this,new SpacePanel.Actions(){
            public void launch(AppCatalog.App app){
                AppCatalog.record(HoverService.this,app);
            }
            public void settings(){suppressed=true;hide();startActivity(new Intent(HoverService.this,MainActivity.class).putExtra("settings",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
            public void close(){suppressed=true;hide();}
        },true);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_FULLSCREEN,PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        p.gravity=Gravity.TOP|Gravity.START;p.setTitle("折境空间 · 半展开");p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        try{windows.addView(next,p);overlay=next;lastAutoPackage=running==null?"":running.packageName;lastSourceTask=running==null?-1:running.existingTaskId;
            if(running!=null)next.post(()->{if(overlay==next)next.openAutomatically(running);});
            next.status(monitor.angle(),monitor.hovering(),monitor.available());Log.i("FoldSpace","overlay shown autoPackage="+lastAutoPackage);diagnostics(monitor.angle(),monitor.hovering(),monitor.available(),true);}
        catch(RuntimeException e){next.dispose();Log.e("FoldSpace","overlay unavailable",e);}
    }
    private void hide(){
        if(entryProbe!=null){entryProbe.cancel();entryProbe=null;}
        if(overlay==null)return;
        SpacePanel old=overlay;overlay=null;old.dispose();try{windows.removeView(old);}catch(IllegalArgumentException ignored){}
        Log.i("FoldSpace","overlay hidden");
    }
    private void diagnostics(float angle,boolean hover,boolean available,boolean changed){
        long now=SystemClock.elapsedRealtime();if(!changed && now-lastWrite<1000)return;lastWrite=now;
        try{
            File dir=getExternalFilesDir(null);if(dir==null)return;
            JSONObject obj=new JSONObject();obj.put("timestamp",System.currentTimeMillis());obj.put("angle",Float.isNaN(angle)?JSONObject.NULL:angle);
            obj.put("hover",hover);obj.put("sensorAvailable",available);obj.put("overlayVisible",overlay!=null);obj.put("suppressed",suppressed);
            obj.put("autoPackage",lastAutoPackage);obj.put("sourceTaskId",lastSourceTask);obj.put("range","110-140");obj.put("holdMs",FoldPolicy.HOLD_MS);
            try(FileOutputStream out=new FileOutputStream(new File(dir,"posture-status.json"))){out.write(obj.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }catch(Exception e){Log.w("FoldSpace","diagnostics unavailable",e);}
    }
    @Override public void onDestroy(){if(monitor!=null)monitor.stop();hide();if(receiverRegistered)unregisterReceiver(screen);instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
