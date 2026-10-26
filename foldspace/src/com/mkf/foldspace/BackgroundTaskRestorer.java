package com.mkf.foldspace;

import android.app.ActivityManager;
import android.content.Context;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;
import java.util.*;

/** Shell-only task placement. No organizer registration, launcher Intent or foreground operation. */
final class BackgroundTaskRestorer {
    private final Context context;
    BackgroundTaskRestorer(Context context){this.context=context;}
    private Object organizer()throws Exception{
        Method method=Class.forName("android.window.WindowOrganizer").getDeclaredMethod("getWindowOrganizerController");method.setAccessible(true);
        return Class.forName("android.window.IWindowOrganizerController").getMethod("getTaskOrganizerController").invoke(method.invoke(null));
    }
    private Object token(Object info)throws Exception{return info.getClass().getField("token").get(info);}
    private int display(Object info)throws Exception{return info.getClass().getField("displayId").getInt(info);}
    private ActivityManager.RunningTaskInfo task(int id){for(ActivityManager.RunningTaskInfo info:context.getSystemService(ActivityManager.class).getRunningTasks(100))if(info.taskId==id)return info;return null;}
    boolean restore(ActivityManager.RunningTaskInfo hosted,int destination){
        try{
            Object controller=organizer();Class<?> api=Class.forName("android.window.ITaskOrganizerController"),container=Class.forName("android.window.WindowContainerToken"),transaction=Class.forName("android.window.WindowContainerTransaction");
            // Resolve every required API before creating a system task, so an unsupported
            // platform cannot leave an empty staging root merely because a method is absent.
            hosted.getClass().getField("launchCookies");
            api.getMethod("createRootTask",int.class,int.class,IBinder.class,boolean.class);api.getMethod("getRootTasks",int.class,int[].class);
            api.getMethod("getChildTasks",container,int[].class);api.getMethod("deleteRootTask",container);
            transaction.getMethod("reorder",container,boolean.class);transaction.getMethod("reparent",container,container,boolean.class);
            Class.forName("android.window.WindowOrganizer").getMethod("applyTransaction",transaction);
            Object child=token(hosted),changes=transaction.getConstructor().newInstance();IBinder cookie=new Binder();
            return BackgroundReturn.restore(new BackgroundReturn.Bridge(){
                public Object createRoot()throws Exception{
                    api.getMethod("createRootTask",int.class,int.class,IBinder.class,boolean.class).invoke(controller,destination,1,cookie,false);
                    List<?> roots=(List<?>)api.getMethod("getRootTasks",int.class,int[].class).invoke(controller,destination,null);
                    for(Object info:roots){List<?> cookies=(List<?>)info.getClass().getField("launchCookies").get(info);if(cookies!=null && cookies.contains(cookie) && display(info)==destination)return token(info);}
                    throw new IllegalStateException("Background return root not found");
                }
                public void lowerRoot(Object root)throws Exception{transaction.getMethod("reorder",container,boolean.class).invoke(changes,root,false);}
                public void moveIntoRoot(Object root)throws Exception{transaction.getMethod("reparent",container,container,boolean.class).invoke(changes,child,root,false);}
                public void detachAtBottom()throws Exception{transaction.getMethod("reparent",container,container,boolean.class).invoke(changes,child,null,false);}
                public boolean commitAndConfirm()throws Exception{
                    // Hierarchy operations execute in order in one transaction: route to the
                    // destination, then leave its temporary parent at the bottom of that display.
                    Class<?> organizerClass=Class.forName("android.window.WindowOrganizer");organizerClass.getMethod("applyTransaction",transaction).invoke(organizerClass.getConstructor().newInstance(),changes);
                    ActivityManager.RunningTaskInfo returned=task(hosted.taskId);return returned!=null && display(returned)==destination;
                }
                public boolean empty(Object root)throws Exception{return ((List<?>)api.getMethod("getChildTasks",container,int[].class).invoke(controller,root,null)).isEmpty();}
                public void delete(Object root)throws Exception{api.getMethod("deleteRootTask",container).invoke(controller,root);}
            });
        }catch(Exception error){Log.w("FoldSpaceHost","Atomic background return unavailable",error);return false;}
    }
    boolean lowerAfterDisplayRemoval(int id,int destination){
        try{
            for(int attempt=0;attempt<24;attempt++){
                ActivityManager.RunningTaskInfo current=task(id);
                if(current==null)return true;
                if(display(current)==destination){
                    Class<?> container=Class.forName("android.window.WindowContainerToken"),transaction=Class.forName("android.window.WindowContainerTransaction"),organizerClass=Class.forName("android.window.WindowOrganizer");
                    Object changes=transaction.getConstructor().newInstance();transaction.getMethod("reorder",container,boolean.class).invoke(changes,token(current),false);
                    organizerClass.getMethod("applyTransaction",transaction).invoke(organizerClass.getConstructor().newInstance(),changes);return true;
                }
                SystemClock.sleep(50);
            }
        }catch(Exception error){Log.w("FoldSpaceHost","Post-removal background placement unavailable",error);}return false;
    }
}
