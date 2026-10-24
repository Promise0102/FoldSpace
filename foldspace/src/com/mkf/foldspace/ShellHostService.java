package com.mkf.foldspace;

import android.app.UiAutomation;
import android.content.*;
import android.graphics.Rect;
import android.hardware.display.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.accessibility.*;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs only in the explicitly authorized Shizuku shell user service, not in the app UID. */
public final class ShellHostService extends Binder {
    private final HandlerThread workerThread=new HandlerThread("upper-host");
    private final HandlerThread automationEvents=new HandlerThread("upper-accessibility-events");
    private final Handler worker;
    private final Context shellContext;
    private final int ownerUid;
    private VirtualDisplay display;
    private int width,height;
    private String targetPackage="";
    private int restoreTaskId=-1,restoreDisplayId=0;
    private String transfer="",continuity="";
    private UiAutomation automation;
    private boolean automationAttempted;
    private Method inject, setDisplayId;
    private Object inputManager;
    private AccessibilityNodeInfo seekNode;
    private Rect seekBounds;
    private long nextTreeQuery;
    // Without DESTROY_CONTENT_ON_REMOVAL: system moves remaining tasks home instead of finishing them.
    // PUBLIC | PRESENTATION | OWN_CONTENT | TOUCH | TRUSTED | OWN_FOCUS | NO_FOCUS_STEAL
    private final int trustedFlags=1|2|8|64|1024|16384|65536;

    public ShellHostService() throws Exception {
        Class<?> threadClass=Class.forName("android.app.ActivityThread");
        Object thread=threadClass.getMethod("currentActivityThread").invoke(null);
        if(thread==null) thread=threadClass.getMethod("systemMain").invoke(null);
        Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
        shellContext=system.createPackageContext("com.android.shell",Context.CONTEXT_IGNORE_SECURITY);
        ownerUid=shellContext.getPackageManager().getApplicationInfo("com.mkf.foldspace",0).uid;
        workerThread.start();automationEvents.start();worker=new Handler(workerThread.getLooper());
    }
    public ShellHostService(Context ignored) throws Exception { this(); }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
        if(code==16777115){int uid=Binder.getCallingUid();if(uid!=ownerUid && uid!=2000 && uid!=0)throw new SecurityException("Not owner");worker.post(()->{release();System.exit(0);});if(reply!=null)reply.writeNoException();return true;}
        if(code<1 || code>9)return super.onTransact(code,data,reply,flags);
        if(Binder.getCallingUid()!=ownerUid)throw new SecurityException("Only FoldSpace may control this display");
        data.enforceInterface(HostProtocol.TOKEN);
        final int session=code==HostProtocol.CREATE || code==HostProtocol.CURRENT_APP?-1:data.readInt();
        try {
            if(code==HostProtocol.CURRENT_APP){String current=run(()->foreground().toString());reply.writeNoException();reply.writeString(current);
            }else if(code==HostProtocol.CREATE){
                Surface surface=Surface.CREATOR.createFromParcel(data);int w=data.readInt(),h=data.readInt(),dpi=data.readInt();
                int id;
                try{id=run(()->{release();width=checked(w,160,4096);height=checked(h,120,4096);int density=checked(dpi,120,640);
                    display=shellContext.getSystemService(DisplayManager.class).createVirtualDisplay("FoldSpace upper app",width,height,density,surface,trustedFlags,null,worker);
                    if(display==null)throw new IllegalStateException("Display creation returned null");configureDisplay(display.getDisplay().getDisplayId());return display.getDisplay().getDisplayId();});}
                finally{surface.release();}reply.writeNoException();reply.writeInt(id);
            }else if(code==HostProtocol.LAUNCH){
                Intent intent=Intent.CREATOR.createFromParcel(data);
                int taskId=data.readInt(),sourceDisplay=data.readInt();
                String result=runHost(session,()->launch(intent,taskId,sourceDisplay));reply.writeNoException();reply.writeString(result);
            }else if(code==HostProtocol.TOUCH){
                MotionEvent event=MotionEvent.CREATOR.createFromParcel(data);
                boolean ok;try{ok=runHost(session,()->input(event));}finally{event.recycle();}reply.writeNoException();reply.writeInt(ok?1:0);
            }else if(code==HostProtocol.RESIZE){int w=data.readInt(),h=data.readInt(),dpi=data.readInt();runHost(session,()->{width=checked(w,160,4096);height=checked(h,120,4096);display.resize(width,height,checked(dpi,120,640));return null;});reply.writeNoException();
            }else if(code==HostProtocol.RELEASE){String result=run(()->{if(display!=null && display.getDisplay().getDisplayId()==session)release();return transfer+"; "+continuity;});reply.writeNoException();reply.writeString(result);
            }else if(code==HostProtocol.STATUS){String state=runHost(session,()->status().toString());reply.writeNoException();reply.writeString(state);
            }else if(code==HostProtocol.SEEK){float fraction=data.readFloat();float[] line=data.createFloatArray();boolean ok=runHost(session,()->seek(fraction,line));reply.writeNoException();reply.writeInt(ok?1:0);
            }else if(code==HostProtocol.KEY){int key=data.readInt();if(key!=KeyEvent.KEYCODE_BACK)throw new SecurityException("Unsupported key");runHost(session,()->{long t=SystemClock.uptimeMillis();KeyEvent down=new KeyEvent(t,t,KeyEvent.ACTION_DOWN,key,0);KeyEvent up=new KeyEvent(t,t,KeyEvent.ACTION_UP,key,0);injectEvent(down);injectEvent(up);return null;});reply.writeNoException();}
        }catch(Exception error){Throwable cause=error instanceof ExecutionException?error.getCause():error;Log.e("FoldSpaceHost","command "+code+" failed",cause);reply.writeException(new IllegalStateException(cause.toString()));}
        return true;
    }
    private <T>T run(Callable<T> task)throws Exception{FutureTask<T> future=new FutureTask<>(task);worker.post(future);return future.get(12,TimeUnit.SECONDS);}
    private <T>T runHost(int session,Callable<T> task)throws Exception{return run(()->{if(display==null || display.getDisplay().getDisplayId()!=session)throw new IllegalStateException("This upper-screen session has ended");return task.call();});}
    private static int checked(int value,int min,int max){if(value<min || value>max)throw new IllegalArgumentException("Display geometry outside supported bounds");return value;}
    private void configureDisplay(int id){
        // Changes apply only to our temporary display, never to the phone's physical display.
        for(String[] command:new String[][]{{"/system/bin/wm","set-ignore-orientation-request","-d",String.valueOf(id),"true"},{"/system/bin/wm","user-rotation","-d",String.valueOf(id),"lock","0"}}){
            try{java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();if(!p.waitFor(2,TimeUnit.SECONDS)){p.destroy();Log.w("FoldSpaceHost","Temporary display rotation configuration timed out");}else if(p.exitValue()!=0)Log.w("FoldSpaceHost","Temporary display rotation configuration unavailable");}
            catch(Exception e){Log.w("FoldSpaceHost","Temporary display configuration unavailable",e);}
        }
    }
    private java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks(){return shellContext.getSystemService(android.app.ActivityManager.class).getRunningTasks(40);}
    private int displayOf(android.app.ActivityManager.RunningTaskInfo task){try{return task.getClass().getField("displayId").getInt(task);}catch(ReflectiveOperationException e){throw new IllegalStateException("Task display metadata unavailable",e);}}
    private android.app.ActivityManager.RunningTaskInfo task(int id){for(android.app.ActivityManager.RunningTaskInfo task:tasks())if(task.taskId==id)return task;return null;}
    private JSONObject foreground()throws Exception{
        for(android.app.ActivityManager.RunningTaskInfo task:tasks()){
            if(displayOf(task)!=Display.DEFAULT_DISPLAY || task.topActivity==null)continue;
            String pkg=task.topActivity.getPackageName();
            if(pkg.equals("com.mkf.foldspace") || pkg.equals("com.android.systemui"))return new JSONObject();
            Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);android.content.pm.ResolveInfo launcher=shellContext.getPackageManager().resolveActivity(home,0);
            if(launcher!=null && pkg.equals(launcher.activityInfo.packageName))return new JSONObject();
            try{int type=(Integer)task.getClass().getMethod("getActivityType").invoke(task);if(type==2 || type==3)return new JSONObject();}catch(ReflectiveOperationException ignored){}
            Intent launch=shellContext.getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null || launch.getComponent()==null)return new JSONObject();
            JSONObject result=new JSONObject();result.put("component",launch.getComponent().flattenToString());result.put("taskId",task.taskId);result.put("displayId",displayOf(task));result.put("package",pkg);return result;
        }
        return new JSONObject();
    }
    private boolean moveTask(int taskId,int destination){
        try{
            java.lang.Process process=new ProcessBuilder("/system/bin/am","display","move-stack",String.valueOf(taskId),String.valueOf(destination)).redirectErrorStream(true).start();
            if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroy();return false;}
            if(process.exitValue()!=0)return false;
            for(int i=0;i<8;i++){android.app.ActivityManager.RunningTaskInfo moved=task(taskId);if(moved!=null && displayOf(moved)==destination)return true;SystemClock.sleep(50);}
        }catch(Exception e){Log.w("FoldSpaceHost","Task transfer unavailable",e);}return false;
    }
    private String launch(Intent launch,int existingTaskId,int sourceDisplay)throws Exception{
        if(display==null)throw new IllegalStateException("Create display first");
        ComponentName component=launch.getComponent();if(component==null)throw new IllegalArgumentException("Explicit launcher required");
        Intent check=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component);
        boolean launcher=false;
        for(android.content.pm.ResolveInfo info:shellContext.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
            if(info.activityInfo.exported && component.getPackageName().equals(info.activityInfo.packageName) && component.getClassName().equals(info.activityInfo.name)){launcher=true;break;}
        }
        if(!launcher)throw new IllegalArgumentException("Not an installed launcher activity");
        targetPackage=component.getPackageName();clearSeek();nextTreeQuery=0;
        android.app.ActivityManager.RunningTaskInfo current=existingTaskId>=0?task(existingTaskId):null;
        if(existingTaskId<0)for(android.app.ActivityManager.RunningTaskInfo candidate:tasks()){
            if(displayOf(candidate)==Display.DEFAULT_DISPLAY && candidate.topActivity!=null && targetPackage.equals(candidate.topActivity.getPackageName())){current=candidate;break;}
        }
        if(current!=null && displayOf(current)==Display.DEFAULT_DISPLAY && current.topActivity!=null && targetPackage.equals(current.topActivity.getPackageName())){
            PlaybackCheckpoint checkpoint=capturePlayback(Display.DEFAULT_DISPLAY,current.taskId);
            if(moveTask(current.taskId,display.getDisplay().getDisplayId())){
                restoreTaskId=current.taskId;restoreDisplayId=Display.DEFAULT_DISPLAY;transfer="adopted task="+current.taskId;
                restorePlayback(checkpoint,display.getDisplay().getDisplayId());return transfer;
            }
            throw new IllegalStateException("无法移动现有应用任务，已保留原页面，请退出后重试");
        }
        if(existingTaskId>=0)throw new IllegalStateException("原应用任务已变化，已取消承接以避免重新打开视频");
        // Argument array: user-supplied labels/URLs are never interpreted by a shell.
        java.lang.Process process=new ProcessBuilder("/system/bin/am","start","--display",String.valueOf(display.getDisplay().getDisplayId()),"--windowingMode","1","-a",Intent.ACTION_MAIN,"-c",Intent.CATEGORY_LAUNCHER,"-n",component.flattenToString(),"-f","0x10000000").redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[2048];int read;while((read=process.getInputStream().read(buffer))!=-1)output.write(buffer,0,read);
        String result=output.toString("UTF-8");if(process.waitFor()!=0 || result.contains("Error:"))throw new IllegalStateException(result);
        for(android.app.ActivityManager.RunningTaskInfo hosted:tasks())if(displayOf(hosted)==display.getDisplay().getDisplayId() && hosted.topActivity!=null && targetPackage.equals(hosted.topActivity.getPackageName())){restoreTaskId=hosted.taskId;restoreDisplayId=Display.DEFAULT_DISPLAY;break;}
        transfer="started task="+restoreTaskId;
        Log.i("FoldSpaceHost","launched "+targetPackage+" on display "+display.getDisplay().getDisplayId());return result;
    }
    private void initInput()throws Exception{
        if(inputManager!=null)return;
        Class<?> clazz;try{clazz=Class.forName("android.hardware.input.InputManagerGlobal");}catch(ClassNotFoundException e){clazz=Class.forName("android.hardware.input.InputManager");}
        inputManager=clazz.getMethod("getInstance").invoke(null);inject=clazz.getMethod("injectInputEvent",InputEvent.class,int.class);
        setDisplayId=InputEvent.class.getMethod("setDisplayId",int.class);
    }
    private boolean injectEvent(InputEvent event)throws Exception{if(display==null)return false;return injectAt(event,display.getDisplay().getDisplayId());}
    private boolean injectAt(InputEvent event,int destination)throws Exception{initInput();setDisplayId.invoke(event,destination);return (Boolean)inject.invoke(inputManager,event,0);}
    private boolean input(MotionEvent event)throws Exception{if(display==null)return false;event.setSource(InputDevice.SOURCE_TOUCHSCREEN);return injectEvent(event);}
    private MediaController controller(){
        try{for(MediaController c:shellContext.getSystemService(MediaSessionManager.class).getActiveSessions(null))if(c.getPackageName().equals(targetPackage))return c;}catch(RuntimeException ignored){}
        return null;
    }
    private void initAutomation(){
        if(automationAttempted)return;automationAttempted=true;
        try{
            Class<?> iface=Class.forName("android.app.IUiAutomationConnection");Object connection=Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            Constructor<UiAutomation> ctor=UiAutomation.class.getDeclaredConstructor(Looper.class,iface);ctor.setAccessible(true);automation=ctor.newInstance(automationEvents.getLooper(),connection);
            UiAutomation.class.getMethod("connect",int.class).invoke(automation,1);
            android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
        }catch(Exception e){automation=null;Log.w("FoldSpaceHost","Native progress discovery unavailable",e);}
    }
    private void querySeek(){
        if(display==null || SystemClock.uptimeMillis()<nextTreeQuery)return;nextTreeQuery=SystemClock.uptimeMillis()+2000;
        querySeek(display.getDisplay().getDisplayId());
    }
    private void querySeek(int destination){
        clearSeek();initAutomation();if(automation==null)return;
        Display target=shellContext.getSystemService(DisplayManager.class).getDisplay(destination);if(target==null)return;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();target.getRealMetrics(metrics);int queryWidth=metrics.widthPixels,queryHeight=metrics.heightPixels;
        List<AccessibilityWindowInfo> windows=automation.getWindowsOnAllDisplays().get(destination);if(windows==null)return;
        int inspected=0;
        for(AccessibilityWindowInfo window:windows){AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);
            while(!queue.isEmpty()){
                AccessibilityNodeInfo node=queue.removeFirst();
                if(++inspected>700){node.recycle();while(!queue.isEmpty())queue.removeFirst().recycle();break;}
                CharSequence pkg=node.getPackageName();String cls=String.valueOf(node.getClassName());Rect bounds=new Rect();node.getBoundsInScreen(bounds);
                AccessibilityNodeInfo.RangeInfo range=node.getRangeInfo();
                if(targetPackage.contentEquals(pkg==null?"":pkg) && node.isVisibleToUser() && (cls.contains("SeekBar") || range!=null) && bounds.width()>queryWidth*.25f && bounds.height()<queryHeight*.28f && bounds.centerY()>queryHeight*.3f){
                    if(seekNode!=null)seekNode.recycle();seekNode=AccessibilityNodeInfo.obtain(node);seekBounds=new Rect(bounds);
                }
                for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}node.recycle();
            }
        }
    }
    private JSONObject status()throws Exception{
        JSONObject obj=new JSONObject();obj.put("displayId",display==null?-1:display.getDisplay().getDisplayId());obj.put("package",targetPackage);
        obj.put("transfer",transfer);obj.put("continuity",continuity);
        MediaController c=controller();boolean mediaSeek=false;
        if(c!=null){PlaybackState s=c.getPlaybackState();MediaMetadata m=c.getMetadata();long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if(s!=null && duration>0){mediaSeek=(s.getActions()&PlaybackState.ACTION_SEEK_TO)!=0;long position=s.getPosition();if(s.getState()==PlaybackState.STATE_PLAYING)position+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());
                obj.put("duration",duration);obj.put("position",Math.max(0,Math.min(duration,position)));obj.put("playing",s.getState()==PlaybackState.STATE_PLAYING);}}
        if(!mediaSeek){try{querySeek();}catch(RuntimeException e){clearSeek();Log.w("FoldSpaceHost","Native seek discovery failed",e);}}obj.put("mode",mediaSeek?"media":seekBounds!=null?"native":"none");
        if(seekNode!=null){AccessibilityNodeInfo.RangeInfo range=seekNode.getRangeInfo();if(range!=null && range.getMax()>range.getMin())obj.put("fraction",(range.getCurrent()-range.getMin())/(range.getMax()-range.getMin()));}
        return obj;
    }
    private boolean seek(float fraction,float[] line)throws Exception{
        if(display==null || Float.isNaN(fraction))return false;fraction=Math.max(0,Math.min(1,fraction));
        MediaController c=controller();if(c!=null){PlaybackState s=c.getPlaybackState();MediaMetadata m=c.getMetadata();long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);if(s!=null && duration>0 && (s.getActions()&PlaybackState.ACTION_SEEK_TO)!=0){c.getTransportControls().seekTo((long)(fraction*duration));return true;}}
        querySeek();
        if(line==null && seekNode!=null){AccessibilityNodeInfo.RangeInfo range=seekNode.getRangeInfo();if(range!=null){Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,range.getMin()+fraction*(range.getMax()-range.getMin()));if(seekNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args))return true;}}
        if(line==null && seekBounds!=null)line=new float[]{seekBounds.left/(float)width,seekBounds.centerY()/(float)height,seekBounds.right/(float)width,seekBounds.centerY()/(float)height};
        float[] point=ControlGeometry.point(fraction,line,width,height);if(point==null)return false;float x=point[0],y=point[1];
        long down=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);input(e);e.recycle();SystemClock.sleep(50);
        e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,Math.min(width-1,x+1),y,0);input(e);e.recycle();SystemClock.sleep(30);
        e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);input(e);e.recycle();return true;
    }
    private void clearSeek(){if(seekNode!=null){seekNode.recycle();seekNode=null;}seekBounds=null;}
    private static final class PlaybackCheckpoint{
        final int taskId;long position,duration;String mediaId="";float fraction=Float.NaN,min,max;
        PlaybackCheckpoint(int taskId){this.taskId=taskId;}
        boolean available(){return duration>0 || !Float.isNaN(fraction);}
    }
    private PlaybackCheckpoint capturePlayback(int source,int taskId){
        PlaybackCheckpoint saved=new PlaybackCheckpoint(taskId);continuity="unavailable";
        try{
            MediaController c=controller();if(c!=null){PlaybackState s=c.getPlaybackState();MediaMetadata m=c.getMetadata();
                if(s!=null && m!=null && (s.getActions()&PlaybackState.ACTION_SEEK_TO)!=0){saved.duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION);saved.position=s.getPosition();saved.mediaId=String.valueOf(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));}}
            if(saved.duration<=0){querySeek(source);if(seekNode!=null){AccessibilityNodeInfo.RangeInfo r=seekNode.getRangeInfo();
                if(r!=null && r.getMax()>r.getMin()){saved.min=r.getMin();saved.max=r.getMax();saved.fraction=(r.getCurrent()-r.getMin())/(r.getMax()-r.getMin());}}}
            if(saved.available())continuity="captured";
        }catch(Exception e){Log.w("FoldSpaceHost","Playback checkpoint unavailable",e);}return saved;
    }
    private void restorePlayback(PlaybackCheckpoint saved,int destination){
        if(saved==null || !saved.available())return;
        // Bounded retry only while transferring this same task; never retains checkpoints for later videos.
        long started=SystemClock.uptimeMillis(),deadline=started+2200;int attempts=0;
        do{SystemClock.sleep(300);
            try{
                android.app.ActivityManager.RunningTaskInfo current=task(saved.taskId);
                if(current==null || displayOf(current)!=destination || current.topActivity==null || !targetPackage.equals(current.topActivity.getPackageName())){continuity="task changed";return;}
                if(saved.duration>0){MediaController c=controller();if(c==null)continue;MediaMetadata m=c.getMetadata();PlaybackState s=c.getPlaybackState();
                    if(m==null || s==null || (s.getActions()&PlaybackState.ACTION_SEEK_TO)==0)continue;
                    if(saved.duration!=m.getLong(MediaMetadata.METADATA_KEY_DURATION) || !saved.mediaId.equals(String.valueOf(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)))){continuity="media changed";return;}
                    if(s.getPosition()+1200<saved.position){c.getTransportControls().seekTo(saved.position);continuity="media position restored";return;}
                    if(SystemClock.uptimeMillis()-started<1000)continue;continuity="media position retained";return;
                }
                querySeek(destination);if(seekNode==null)continue;AccessibilityNodeInfo.RangeInfo range=seekNode.getRangeInfo();
                if(range==null || range.getMax()!=saved.max || range.getMin()!=saved.min)continue;
                float after=(range.getCurrent()-range.getMin())/(range.getMax()-range.getMin());
                if(!PlaybackContinuity.needsRestore(saved.fraction,after)){if(SystemClock.uptimeMillis()-started<1000)continue;continuity=(attempts>0?"native position restored":"native position retained")+" before="+saved.fraction+" after="+after;return;}
                Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,saved.min+saved.fraction*(saved.max-saved.min));
                boolean supported=attempts++==0 && seekNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args);
                if(!supported && seekBounds!=null){
                    float x=seekBounds.left+saved.fraction*seekBounds.width(),y=seekBounds.centerY();long t=SystemClock.uptimeMillis();
                    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                        MotionEvent event=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                        try{supported=injectAt(event,destination);}finally{event.recycle();}SystemClock.sleep(40);
                    }
                }
                continuity=supported?"native position restored (checking)":"native restore unsupported";
            }catch(Exception e){Log.w("FoldSpaceHost","Playback restore unavailable",e);}
        }while(SystemClock.uptimeMillis()<deadline);
        if(continuity.endsWith("(checking)"))continuity="native restore not confirmed";
    }
    private void release(){
        if(display!=null && restoreTaskId>=0){android.app.ActivityManager.RunningTaskInfo current=task(restoreTaskId);if(current!=null && displayOf(current)==display.getDisplay().getDisplayId()){
            PlaybackCheckpoint checkpoint=capturePlayback(display.getDisplay().getDisplayId(),restoreTaskId);
            boolean restored=moveTask(restoreTaskId,restoreDisplayId);transfer="restored task="+restoreTaskId+" success="+restored;if(restored)restorePlayback(checkpoint,restoreDisplayId);
            Log.i("FoldSpaceHost","restored task="+restoreTaskId+" success="+restored);}}
        restoreTaskId=-1;clearSeek();targetPackage="";if(display!=null){display.release();display=null;}if(automation!=null){try{UiAutomation.class.getMethod("disconnect").invoke(automation);}catch(Exception ignored){}automation=null;}automationAttempted=false;
    }
}
