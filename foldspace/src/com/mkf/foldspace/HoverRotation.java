package com.mkf.foldspace;

import android.app.Activity;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import java.util.*;

/** Manual, Activity-scoped direction control; never writes global auto-rotate settings. */
final class HoverRotation {
    private static final Map<String,HoverRotation> OWNERS=new HashMap<>();
    private final View view;
    private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String token=UUID.randomUUID().toString();
    private OrientationSession session;
    private RotationActivity helper,retiringHelper;
    private final ArrayList<Runnable> closeCallbacks=new ArrayList<>();
    private boolean closeFinished;
    private final Runnable closeTimeout=this::completeClose;
    private boolean closed,pending;
    private long deadline,wideSince=-1;
    HoverRotation(View view,Runnable changed){this.view=view;this.changed=changed;}
    boolean isClosed(){return closed;}
    boolean isPending(){return !closed && pending;}
    static HoverRotation owner(String token){return OWNERS.get(token);}
    void start(){
        if(closed || pending || session!=null)return;
        pending=true;deadline=SystemClock.uptimeMillis()+5000;changed.run();
        try{
            if(view.getLayoutParams() instanceof WindowManager.LayoutParams){
                // ColorOS may ignore orientation on TYPE_APPLICATION_OVERLAY. Use an opaque
                // Activity behind the existing visible overlay, just as a reader owns its page.
                OWNERS.put(token,this);
                Intent intent=new Intent(view.getContext(),RotationActivity.class).putExtra("rotation_owner",token)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS|Intent.FLAG_ACTIVITY_NO_ANIMATION);
                view.getContext().startActivity(intent);
            }else{
                Context context=view.getContext();
                while(context instanceof ContextWrapper && !(context instanceof Activity))context=((ContextWrapper)context).getBaseContext();
                if(!(context instanceof Activity))throw new IllegalStateException("No Activity available for orientation");
                attach((Activity)context);
            }
            main.postDelayed(verify,150);
        }catch(RuntimeException error){fail("横屏页面未能启动，请返回折境空间后重试",error);}
    }
    void attachHelper(RotationActivity activity){
        if(closed || OWNERS.get(token)!=this){activity.finishAndRemoveTask();return;}
        helper=activity;
        try{attach(activity);}catch(RuntimeException error){fail("系统未接受横屏请求",error);}
    }
    private void attach(Activity activity){
        if(session!=null)return;
        session=new OrientationSession(new OrientationSession.Window(){
            public int requested(){return activity.getRequestedOrientation();}
            public void request(int orientation){activity.setRequestedOrientation(orientation);}
        });
        session.start();Log.i("FoldSpaceRotation","Manual Activity landscape requested");
    }
    void helperStopped(RotationActivity activity){if(retiringHelper==activity)completeClose();else if(helper==activity && !closed)close();}
    private final Runnable verify=new Runnable(){public void run(){
        if(closed)return;
        long now=SystemClock.uptimeMillis();
        Display display=view.getContext().getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode mode=display==null?null:display.getMode();int rotation=display==null?-1:display.getRotation();
        boolean wide=session!=null && mode!=null && PhysicalOrientation.landscape(mode.getPhysicalWidth(),mode.getPhysicalHeight(),rotation);
        if(wide){if(wideSince<0)wideSince=now;if(now-wideSince>=350){pending=false;changed.run();Log.i("FoldSpaceRotation","Landscape confirmed mode="+mode.getPhysicalWidth()+"x"+mode.getPhysicalHeight()+" rotation="+rotation);return;}}
        else wideSince=-1;
        if(now>=deadline){fail(session==null?"横屏页面未启动，请在折境空间内重试":"系统尚未执行横屏，请检查本应用的大屏方向设置",null);return;}
        main.postDelayed(this,150);
    }};
    private void fail(String message,Exception error){
        Log.w("FoldSpaceRotation",message,error);close();Toast.makeText(view.getContext(),message,Toast.LENGTH_LONG).show();
    }
    void close(){close(null);}
    void close(Runnable completion){
        if(completion!=null){if(closeFinished){main.post(completion);return;}closeCallbacks.add(completion);}
        if(closed)return;closed=true;pending=false;main.removeCallbacks(verify);OWNERS.remove(token);
        OrientationSession current=session;session=null;
        if(current!=null)try{current.close();}catch(RuntimeException error){Log.w("FoldSpaceRotation","Activity already removed while releasing orientation",error);}
        RotationActivity old=helper;helper=null;changed.run();
        if(old!=null && !old.isDestroyed()){
            retiringHelper=old;main.postDelayed(closeTimeout,1500);if(!old.isFinishing())old.finishAndRemoveTask();
        }else completeClose();
    }
    private void completeClose(){
        if(closeFinished)return;closeFinished=true;retiringHelper=null;main.removeCallbacks(closeTimeout);
        ArrayList<Runnable> callbacks=new ArrayList<>(closeCallbacks);closeCallbacks.clear();for(Runnable done:callbacks)done.run();
    }
}
