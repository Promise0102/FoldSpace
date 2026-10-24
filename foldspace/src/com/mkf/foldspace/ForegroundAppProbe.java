package com.mkf.foldspace;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

/** Takes one foreground-task snapshot before the hover window is added. Never asks in background. */
public final class ForegroundAppProbe {
    public interface Callback{void ready(AppCatalog.App app);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private boolean closed,bound;
    private Callback callback;
    private final Runnable timeout=()->finish(null);
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){
            if(closed)return;
            io.execute(()->{
                String json=null;Parcel in=Parcel.obtain(),out=Parcel.obtain();
                try{in.writeInterfaceToken(HostProtocol.TOKEN);if(service.transact(HostProtocol.CURRENT_APP,in,out,0)){out.readException();json=out.readString();}}
                catch(Exception e){android.util.Log.w("FoldSpaceForeground","Foreground task lookup unavailable",e);}finally{in.recycle();out.recycle();}
                String result=json;main.post(()->resolve(result));
            });
        }
        public void onServiceDisconnected(ComponentName name){finish(null);}
    };
    public ForegroundAppProbe(Context c){context=c.getApplicationContext();args=AppHostController.userServiceArgs(context);}
    public void start(Callback result){
        callback=result;
        try{if(!Shizuku.pingBinder() || Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){finish(null);return;}
            bound=true;main.postDelayed(timeout,2500);Shizuku.bindUserService(args,connection);
        }catch(RuntimeException e){finish(null);}
    }
    private void resolve(String json){
        if(closed)return;
        try{JSONObject task=new JSONObject(json==null?"{}":json);ComponentName component=ComponentName.unflattenFromString(task.optString("component",""));
            if(component==null){finish(null);return;}
            AppCatalog.resolveRunning(context,component,task.getInt("taskId"),task.getInt("displayId"),this::finish);
        }catch(Exception e){finish(null);}
    }
    private void finish(AppCatalog.App app){if(closed)return;Callback result=callback;cancel();if(result!=null)result.ready(app);}
    public void cancel(){
        if(closed)return;closed=true;main.removeCallbacks(timeout);io.shutdown();
        if(bound)try{Shizuku.unbindUserService(args,connection,false);}catch(RuntimeException ignored){}
    }
}
