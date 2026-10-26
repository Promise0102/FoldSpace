package com.mkf.foldspace;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppCatalog {
    public static final class App {
        public final String packageName, label, category;
        public final Intent intent;
        public final Bitmap icon;
        public final int existingTaskId,originalDisplayId;
        App(String pkg, String label, String category, Intent intent, Bitmap icon) {
            this(pkg,label,category,intent,icon,-1,0);
        }
        App(String pkg,String label,String category,Intent intent,Bitmap icon,int taskId,int originalDisplayId){
            this.packageName = pkg; this.label = label; this.category = category; this.intent = intent; this.icon = icon;
            this.existingTaskId=taskId;this.originalDisplayId=originalDisplayId;
        }
    }
    public interface Callback { void loaded(List<App> apps); }
    public interface RunningCallback{void loaded(App app);}
    public static void resolveRunning(Context context,android.content.ComponentName component,int taskId,int displayId,RunningCallback callback){
        Context c=context.getApplicationContext();IO.execute(()->{
            App result=null;
            try{
                PackageManager pm=c.getPackageManager();Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ResolveInfo ri=pm.resolveActivity(intent,0);
                if(ri!=null && ri.activityInfo.exported){String label=ri.loadLabel(pm).toString();Drawable drawable=ri.loadIcon(pm);Bitmap icon=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,144,144);drawable.draw(new Canvas(icon));String type=category(ri.activityInfo.applicationInfo,component.getPackageName(),label);result=new App(component.getPackageName(),label,type,intent,icon,taskId,displayId);}
            }catch(RuntimeException ignored){}
            App resolved=result;MAIN.post(()->callback.loaded(resolved));
        });
    }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static List<App> cached;
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("foldspace", Context.MODE_PRIVATE); }
    public static void load(Context context, Callback callback) {
        if (cached != null) { callback.loaded(new ArrayList<>(cached)); return; }
        Context appContext = context.getApplicationContext();
        IO.execute(() -> {
            PackageManager pm = appContext.getPackageManager();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<App> result = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(appContext.getPackageName()) || !seen.add(pkg)) continue;
                try {
                    String label = ri.loadLabel(pm).toString();
                    String appCategory=category(ri.activityInfo.applicationInfo,pkg,label);
                    if(!appCategory.equals("视频") && !appCategory.equals("游戏"))continue;
                    Drawable drawable = ri.loadIcon(pm);
                    Bitmap icon = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0, 0, 144, 144); drawable.draw(new Canvas(icon));
                    Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(pkg, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    result.add(new App(pkg, label, appCategory, launch, icon));
                } catch (RuntimeException ignored) { /* One unavailable launcher must not break the directory. */ }
            }
            Collator collator = Collator.getInstance(Locale.CHINA);
            Collections.sort(result, (a, b) -> collator.compare(a.label, b.label));
            MAIN.post(() -> { cached = result; callback.loaded(new ArrayList<>(result)); });
        });
    }
    private static String category(android.content.pm.ApplicationInfo info,String pkg,String label) {
        boolean game=info.category==android.content.pm.ApplicationInfo.CATEGORY_GAME || (info.flags&android.content.pm.ApplicationInfo.FLAG_IS_GAME)!=0;
        return WorkspacePolicy.category(pkg,label,info.category==android.content.pm.ApplicationInfo.CATEGORY_VIDEO,game);
    }
    public static long lastUsed(Context c, App app) { return prefs(c).getLong("recent." + app.packageName, 0); }
    public static void record(Context c, App app) { prefs(c).edit().putLong("recent." + app.packageName, System.currentTimeMillis()).putString("paired."+app.category,app.packageName).apply(); }
    public static List<App> featured(Context c, List<App> apps) {
        List<App> sorted = new ArrayList<>(apps);
        Collections.sort(sorted, (a,b) -> Long.compare(score(c,b), score(c,a)));
        return new ArrayList<>(sorted.subList(0, Math.min(4, sorted.size())));
    }
    private static long score(Context c, App a) {
        long recent = lastUsed(c,a); if (recent != 0) return recent;
        if (a.packageName.equals("com.ss.android.ugc.aweme")) return 900;
        if (a.packageName.contains("bilibili")) return 800;
        if (a.category.equals("音乐")) return 700;
        return 0;
    }
}
