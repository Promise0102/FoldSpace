package com.mkf.foldspace;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    static boolean visible;
    private SpacePanel panel;
    private FoldMonitor monitor;
    private boolean awaitingOverlay;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);ImmersiveMode.apply(getWindow());
        panel=new SpacePanel(this,new SpacePanel.Actions(){
            public void launch(AppCatalog.App app){
                AppCatalog.record(MainActivity.this,app);
            }
            public void setAuto(boolean enabled){if(enabled)requestAuto();else{AppCatalog.prefs(MainActivity.this).edit().putBoolean("auto",false).apply();stopService(new Intent(MainActivity.this,HoverService.class));refreshStatus();}}
            public void restart(){stopService(new Intent(MainActivity.this,HoverService.class));requestAuto();}
            public void overlayPermission(){openOverlayPermission();}
            public void shizuku(){openShizuku();}
            public void close(){finish();}
        },false); setContentView(panel);
        monitor=new FoldMonitor(this,(a,h,ok)->panel.status(a,h,ok));
        panel.post(this::applySettingsIntent);
    }
    @Override protected void onResume(){
        super.onResume();visible=true;HoverService.suppressCurrent();monitor.start();panel.refreshRecents();panel.post(()->ImmersiveMode.apply(getWindow()));
        if(AppCatalog.prefs(this).getBoolean("auto",false) && Settings.canDrawOverlays(this) && monitor.available()){
            try{startForegroundService(new Intent(this,HoverService.class));}catch(RuntimeException e){android.util.Log.w("FoldSpace","Resume monitor unavailable",e);}
        }
        if(awaitingOverlay){awaitingOverlay=false;if(Settings.canDrawOverlays(this)) enableAuto();else Toast.makeText(this,"悬浮窗未开启，暂时只能手动打开",Toast.LENGTH_LONG).show();}
        panel.refreshSettings();
    }
    @Override protected void onPause(){visible=false;monitor.stop();panel.suspendRotation();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)ImmersiveMode.apply(getWindow());}
    @Override protected void onDestroy(){panel.dispose();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);applySettingsIntent();}
    @Override public void onBackPressed(){if(!panel.hideSettings())super.onBackPressed();}
    private void applySettingsIntent(){
        Intent intent=getIntent();String action=intent.getStringExtra("settings_action");
        if(intent.getBooleanExtra("settings",false) || action!=null){panel.showSettings();intent.removeExtra("settings");intent.removeExtra("settings_action");}
        if("overlay".equals(action))openOverlayPermission();else if("shizuku".equals(action))openShizuku();else if("restart".equals(action)){stopService(new Intent(this,HoverService.class));requestAuto();}
    }
    private void openOverlayPermission(){
        try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
        catch(ActivityNotFoundException e){awaitingOverlay=false;Toast.makeText(this,"请在系统设置中管理本软件的悬浮窗权限",Toast.LENGTH_LONG).show();}
    }
    private void openShizuku(){
        try{
            if(Shizuku.pingBinder()){
                if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)Shizuku.requestPermission(842);
                else Toast.makeText(this,"Shizuku 已连接并授权",Toast.LENGTH_SHORT).show();
            }else{
                Intent manager=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if(manager!=null)startActivity(manager);else Toast.makeText(this,"请先安装并启动 Shizuku",Toast.LENGTH_LONG).show();
            }
        }catch(RuntimeException e){Toast.makeText(this,"请启动 Shizuku 后重试",Toast.LENGTH_LONG).show();}
    }
    private void requestAuto(){
        if(!monitor.available()){Toast.makeText(this,"没有可用的铰链角度传感器",Toast.LENGTH_LONG).show();return;}
        if(!Settings.canDrawOverlays(this)){
            awaitingOverlay=true;
            openOverlayPermission();
        }else enableAuto();
    }
    private void enableAuto(){
        try {
            startForegroundService(new Intent(this,HoverService.class));
            AppCatalog.prefs(this).edit().putBoolean("auto",true).apply();
            Toast.makeText(this,"自动悬停已开启：110°–140°保持3秒进入，范围外保持3秒退出",Toast.LENGTH_LONG).show();
        } catch(RuntimeException e){AppCatalog.prefs(this).edit().putBoolean("auto",false).apply();Toast.makeText(this,"监测启动失败，请重新打开软件后启用",Toast.LENGTH_LONG).show();android.util.Log.e("FoldSpace","start monitor failed",e);}
        refreshStatus();
    }
    private void refreshStatus(){panel.status(monitor.angle(),monitor.hovering(),monitor.available());}
}
