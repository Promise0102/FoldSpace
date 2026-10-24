package com.mkf.foldspace;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;

public final class MainActivity extends Activity {
    static boolean visible;
    private SpacePanel panel;
    private FoldMonitor monitor;
    private boolean awaitingOverlay, notificationAsked;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);ImmersiveMode.apply(getWindow());
        panel=new SpacePanel(this,new SpacePanel.Actions(){
            public void launch(AppCatalog.App app){
                AppCatalog.record(MainActivity.this,app);
            }
            public void settings(){showSettings();}
            public void close(){finish();}
        },false); setContentView(panel);
        monitor=new FoldMonitor(this,(a,h,ok)->panel.status(a,h,ok));
        if(getIntent().getBooleanExtra("settings",false)) panel.post(this::showSettings);
    }
    @Override protected void onResume(){
        super.onResume();visible=true;monitor.start();panel.refreshRecents();panel.post(()->ImmersiveMode.apply(getWindow()));
        if(AppCatalog.prefs(this).getBoolean("auto",false) && Settings.canDrawOverlays(this) && monitor.available()){
            try{startForegroundService(new Intent(this,HoverService.class));}catch(RuntimeException e){android.util.Log.w("FoldSpace","Resume monitor unavailable",e);}
        }
        if(awaitingOverlay){awaitingOverlay=false;if(Settings.canDrawOverlays(this)) enableAuto();else Toast.makeText(this,"悬浮窗未开启，暂时只能手动打开",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onPause(){visible=false;monitor.stop();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)ImmersiveMode.apply(getWindow());}
    @Override protected void onDestroy(){panel.dispose();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("settings",false)) showSettings();}
    private void showSettings(){
        boolean enabled=AppCatalog.prefs(this).getBoolean("auto",false);
        String message="角度在110°–140°内连续保持3秒进入；在范围外连续保持3秒退出。跨出或返回范围会重新计时。\n\n进入时优先将当前前台应用移到上半屏，退出时尝试放回原屏幕；没有合适应用时显示视频目录。需保持 Shizuku 运行并授权。目录仍只保留视频应用。\n\n视频应用下半屏提供进度和滑动区；切换上方应用有淡入动画。无需 Root。自动出现需要悬浮窗权限和前台监测通知；系统停止服务后需重新开启。\n\n当前："+(enabled?"自动悬停已开启":"自动悬停未开启");
        new AlertDialog.Builder(this).setTitle("自动悬停").setMessage(message)
            .setPositiveButton(enabled?"关闭自动悬停":"开启自动悬停",(d,w)->{if(enabled){AppCatalog.prefs(this).edit().putBoolean("auto",false).apply();stopService(new Intent(this,HoverService.class));refreshStatus();}else requestAuto();})
            .setNeutralButton("重新启动监测",(d,w)->requestAuto()).setNegativeButton("返回",null).show();
    }
    private void requestAuto(){
        if(!monitor.available()){Toast.makeText(this,"没有可用的铰链角度传感器",Toast.LENGTH_LONG).show();return;}
        if(!Settings.canDrawOverlays(this)){
            awaitingOverlay=true;
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException e){awaitingOverlay=false;Toast.makeText(this,"请在系统设置中开启本软件的悬浮窗权限",Toast.LENGTH_LONG).show();}
        }else enableAuto();
    }
    private void enableAuto(){
        try {
            startForegroundService(new Intent(this,HoverService.class));
            AppCatalog.prefs(this).edit().putBoolean("auto",true).apply();
            if(Build.VERSION.SDK_INT>=33 && !notificationAsked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){notificationAsked=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42);}
            Toast.makeText(this,"自动悬停已开启：110°–140°保持3秒进入，范围外保持3秒退出",Toast.LENGTH_LONG).show();
        } catch(RuntimeException e){AppCatalog.prefs(this).edit().putBoolean("auto",false).apply();Toast.makeText(this,"监测启动失败，请重新打开软件后启用",Toast.LENGTH_LONG).show();android.util.Log.e("FoldSpace","start monitor failed",e);}
        refreshStatus();
    }
    private void refreshStatus(){panel.status(monitor.angle(),monitor.hovering(),monitor.available());}
}
