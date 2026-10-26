package com.mkf.foldspace;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

/** App-side client. Every potentially blocking Binder operation stays off the UI thread. */
public final class AppHostController {
    public interface Listener {void ready(int displayId);void error(String message);void state(JSONObject state);void seekResult(boolean ok);}
    private final Context context;
    private final Listener listener;
    private final AppCatalog.App app;
    private final int slot;
    private final IBinder ownerToken=new Binder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs args;
    private IBinder binder;
    private Surface surface;
    private int width,height,density,displayId=-1;
    private static boolean permissionRequested;
    private static final int HOST_PERMISSION_REQUEST=843;
    static {Shizuku.addBinderDeadListener(()->permissionRequested=false);Shizuku.addRequestPermissionResultListener((code,result)->{if(code==HOST_PERMISSION_REQUEST)permissionRequested=false;});}
    private final int requestCode=HOST_PERMISSION_REQUEST;
    private volatile boolean disposed;
    private boolean bound,creating,releaseFinished;
    private final java.util.ArrayList<Runnable> releaseCallbacks=new java.util.ArrayList<>();
    private final Shizuku.OnRequestPermissionResultListener permission=this::permissionResult;
    private void permissionResult(int code,int result){
        if(code!=requestCode || disposed)return;
        if(result==PackageManager.PERMISSION_GRANTED) bind();else listener.error("需要允许 Shizuku，才能在分区内运行应用。可返回目录后重试。");
    }
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){if(disposed)return;binder=service;create();}
        public void onServiceDisconnected(ComponentName name){binder=null;if(!disposed)listener.error("Shizuku 连接中断，请启动 Shizuku 后重试。");}
    };
    public AppHostController(Context c,AppCatalog.App app,int slot,Listener listener){
        context=c.getApplicationContext();this.app=app;this.slot=slot;this.listener=listener;
        args=userServiceArgs(context);
        Shizuku.addRequestPermissionResultListener(permission);
    }
    public static Shizuku.UserServiceArgs userServiceArgs(Context c){return new Shizuku.UserServiceArgs(new ComponentName(c,ShellHostService.class)).daemon(false).tag("foldspace-upper-host").version(HostProtocol.SERVICE_VERSION).processNameSuffix("upper_host");}
    public void prepare(Surface s,int w,int h,int dpi){surface=s;width=w;height=h;density=dpi;authorize();}
    private void authorize(){
        if(disposed)return;
        try{
            if(!Shizuku.pingBinder()){listener.error("请先启动 Shizuku，再返回应用目录重试；本功能无需 Root。");return;}
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)bind();else if(!permissionRequested){permissionRequested=true;try{Shizuku.requestPermission(requestCode);}catch(RuntimeException error){permissionRequested=false;throw error;}}
        }catch(RuntimeException e){report(e);}
    }
    private void bind(){if(disposed || bound)return;try{bound=true;Shizuku.bindUserService(args,connection);}catch(RuntimeException e){bound=false;report(e);}}
    private void create(){
        if(disposed || creating || surface==null || !surface.isValid())return;creating=true;
        io.execute(()->{
            if(disposed)return;
            try{
                displayId=call(HostProtocol.CREATE,p->{surface.writeToParcel(p,0);p.writeInt(width);p.writeInt(height);p.writeInt(density);p.writeInt(slot);p.writeStrongBinder(ownerToken);},Parcel::readInt);
                if(disposed)return;
                String result=call(HostProtocol.LAUNCH,p->{app.intent.writeToParcel(p,0);p.writeInt(app.existingTaskId);p.writeInt(app.originalDisplayId);p.writeInt("视频".equals(app.category)?1:0);},Parcel::readString);record("launch",result);
                main.post(()->{if(!disposed){listener.ready(displayId);}});
            }catch(Exception e){report(e);}
        });
    }
    public void resize(int w,int h,int dpi){width=w;height=h;density=dpi;if(displayId<0 || disposed)return;submit(HostProtocol.RESIZE,p->{p.writeInt(w);p.writeInt(h);p.writeInt(dpi);});}
    public void touch(MotionEvent source){
        if(disposed || displayId<0)return;MotionEvent event=MotionEvent.obtain(source);
        io.execute(()->{try{if(!disposed)call(HostProtocol.TOUCH,p->event.writeToParcel(p,0),Parcel::readInt);}catch(Exception e){report(e);}finally{event.recycle();}});
    }
    public void back(){submit(HostProtocol.KEY,p->p.writeInt(KeyEvent.KEYCODE_BACK));}
    public void seek(float fraction,float[] calibration){
        if(disposed || displayId<0)return;
        io.execute(()->{try{if(disposed)return;boolean ok=call(HostProtocol.SEEK,p->{p.writeFloat(fraction);p.writeFloatArray(calibration);},Parcel::readInt)!=0;main.post(()->{if(!disposed)listener.seekResult(ok);});}catch(Exception e){report(e);}});
    }
    private void submit(int code,Writer writer){if(disposed || displayId<0)return;io.execute(()->{try{if(!disposed)call(code,writer,p->null);}catch(Exception e){report(e);}});}
    public void dispose(){dispose(null);}
    public void dispose(Runnable completion){
        if(completion!=null){if(releaseFinished){main.post(completion);return;}releaseCallbacks.add(completion);}
        if(disposed)return;disposed=true;Shizuku.removeRequestPermissionResultListener(permission);
        io.execute(()->{try{if(binder!=null && displayId>=0)record("release",call(HostProtocol.RELEASE,null,Parcel::readString));}catch(Exception e){record("release error",e.toString());}
            finally{main.post(()->{
                if(bound){bound=false;try{Shizuku.unbindUserService(args,connection,false);}catch(RuntimeException ignored){}}
                releaseFinished=true;java.util.ArrayList<Runnable> callbacks=new java.util.ArrayList<>(releaseCallbacks);releaseCallbacks.clear();for(Runnable done:callbacks)done.run();
            });}
        });io.shutdown();
    }
    private interface Writer{void write(Parcel p);}
    private void record(String event,String value){
        try{java.io.File directory=context.getExternalFilesDir(null);if(directory==null)return;java.io.File file=new java.io.File(directory,"host-events.jsonl");
            JSONObject entry=new JSONObject();entry.put("timestamp",System.currentTimeMillis());entry.put("event",event);entry.put("package",app.packageName);entry.put("displayId",displayId);entry.put("sourceTaskId",app.existingTaskId);entry.put("result",value);
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(file,file.length()<65536)){output.write((entry.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }catch(Exception ignored){}
    }
    private interface Reader<T>{T read(Parcel p);}
    private <T>T call(int code,Writer writer,Reader<T> reader)throws Exception{
        IBinder target=binder;if(target==null || !target.isBinderAlive())throw new IllegalStateException("Shizuku user service unavailable");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(HostProtocol.TOKEN);if(code!=HostProtocol.CREATE)in.writeInt(displayId);if(writer!=null)writer.write(in);if(!target.transact(code,in,out,0))throw new IllegalStateException("Unsupported host operation");out.readException();return reader.read(out);}finally{in.recycle();out.recycle();}
    }
    private void report(Exception e){android.util.Log.e("FoldSpaceHostClient","Host operation failed",e);main.post(()->{if(!disposed)listener.error("分区运行失败："+e.getMessage());});}
}
