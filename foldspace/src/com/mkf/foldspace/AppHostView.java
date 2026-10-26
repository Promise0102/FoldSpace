package com.mkf.foldspace;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Live app above the hinge, unobstructed gesture pad below it. */
public final class AppHostView extends FrameLayout implements TextureView.SurfaceTextureListener,AppHostController.Listener{
    private final TextureView texture;
    private final TextView message;
    private final AppCatalog.App app;
    private final int slot;
    private final Runnable launched;
    private AppHostController controller,closingController;
    private Surface surface;
    private int width,height;
    private boolean disposed,recorded,failed;
    private java.util.function.IntPredicate appSwipe;
    private UpperAppSwipe swipe;
    private Bitmap backdrop;
    private long backdropAt;
    private final Paint backdropPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    public AppHostView(Context c,AppCatalog.App app,int slot,Runnable launched){
        super(c);this.app=app;this.slot=slot;this.launched=launched;setBackgroundColor(0xFF0B1221);
        texture=new TextureView(c);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
        message=SpaceStyle.text(c,"正在连接 Shizuku，准备分区应用…",14,SpaceStyle.WHITE);message.setGravity(Gravity.CENTER);message.setPadding(dp(20),dp(12),dp(20),dp(12));addView(message,new LayoutParams(-1,-1));
        texture.setOnTouchListener((v,event)->{if(controller!=null)controller.touch(event);return true;});
    }
    public View controls(Runnable directory,Runnable settings,Runnable games,TextView rotateButton){
        LinearLayout box=new LinearLayout(getContext());box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar=new LinearLayout(getContext());bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=SpaceStyle.button(getContext(),"返回");back.setContentDescription("返回上方应用；长按返回视频目录");back.setOnClickListener(v->{if(controller!=null)controller.back();});back.setOnLongClickListener(v->{directory.run();return true;});bar.addView(back,new LinearLayout.LayoutParams(dp(66),dp(48)));
        TextView gameButton=SpaceStyle.button(getContext(),"游戏双开");gameButton.setOnClickListener(v->games.run());LinearLayout.LayoutParams gameParams=new LinearLayout.LayoutParams(dp(88),dp(48));gameParams.leftMargin=dp(8);bar.addView(gameButton,gameParams);
        LinearLayout.LayoutParams rotateParams=new LinearLayout.LayoutParams(dp(58),dp(48));rotateParams.leftMargin=dp(8);bar.addView(rotateButton,rotateParams);
        bar.addView(new View(getContext()),new LinearLayout.LayoutParams(0,1,1));
        TextView settingsButton=SpaceStyle.button(getContext(),"设置");settingsButton.setContentDescription("打开下半屏设置");settingsButton.setOnClickListener(v->settings.run());bar.addView(settingsButton,new LinearLayout.LayoutParams(dp(76),dp(48)));
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        View pad=new View(getContext());pad.setBackground(new LiquidGlassDrawable(pad,26));pad.setContentDescription("触控区，轻点及上下滑动同步到上方应用");pad.setFocusable(true);
        pad.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(true);if(controller!=null && width>0 && height>0){MotionEvent mapped=MotionEvent.obtain(event);Matrix transform=new Matrix();transform.setScale(width/(float)Math.max(1,v.getWidth()),height/(float)Math.max(1,v.getHeight()));mapped.transform(transform);controller.touch(mapped);mapped.recycle();}return true;});
        LinearLayout.LayoutParams padParams=new LinearLayout.LayoutParams(-1,0,1);padParams.topMargin=dp(12);box.addView(pad,padParams);
        TextView note=SpaceStyle.text(getContext(),"触控区轻点和滑动均传递到上方应用",10,SpaceStyle.MUTED);note.setGravity(Gravity.CENTER);box.addView(note,new LinearLayout.LayoutParams(-1,dp(26)));return box;
    }
    public void setAppSwipeListener(java.util.function.IntPredicate listener){appSwipe=listener;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(slot!=0 || appSwipe==null)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){swipe=new UpperAppSwipe(ViewConfiguration.get(getContext()).getScaledTouchSlop(),Math.max(dp(48),Math.min(dp(110),getWidth()*.18f)));swipe.start(event.getRawX(),event.getRawY());return false;}
        if(swipe==null)return false;
        if(action==MotionEvent.ACTION_CANCEL){swipe.cancel();return false;}
        // Interception sends ACTION_CANCEL to the hosted app before this view owns the horizontal drag.
        return swipe.move(event.getRawX(),event.getRawY(),event.getPointerCount());
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(slot!=0 || appSwipe==null || swipe==null)return super.onTouchEvent(event);
        int action=event.getActionMasked();
        if(event.getPointerCount()>1 || action==MotionEvent.ACTION_CANCEL){swipe.cancel();restoreDrag();return true;}
        if(action==MotionEvent.ACTION_MOVE){setTranslationX(Math.max(-getWidth()*.16f,Math.min(getWidth()*.16f,swipe.distance(event.getRawX())*.24f)));return true;}
        if(action==MotionEvent.ACTION_UP){int direction=swipe.finish(event.getRawX(),event.getRawY());if(direction==0 || !appSwipe.test(direction))restoreDrag();return true;}
        return true;
    }
    private void restoreDrag(){animate().translationX(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
    public void back(){if(controller!=null)controller.back();}
    public AppCatalog.App app(){return app;}
    public boolean isReusable(){return !disposed && !failed;}
    public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){if(disposed)return;width=w;height=h;surface=new Surface(st);controller=new AppHostController(getContext(),app,slot,this);controller.prepare(surface,w,h,density());}
    public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){width=w;height=h;if(controller!=null)controller.resize(w,h,density());}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st){if(controller!=null){closingController=controller;controller=null;closingController.dispose();}if(surface!=null){surface.release();surface=null;}releaseBackdrop();return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture st){}
    public void dispose(){dispose(null);}
    public void dispose(Runnable completion){
        disposed=true;animate().cancel();texture.animate().cancel();
        if(controller!=null){closingController=controller;controller=null;}
        if(closingController!=null)closingController.dispose(completion);else if(completion!=null)completion.run();
        if(surface!=null){surface.release();surface=null;}releaseBackdrop();
    }
    void drawBackdrop(Canvas canvas){
        // Only the lower game can lie behind our floating controls. Use a bounded, reusable
        // thumbnail instead of reading the full game frame or recursively drawing TextureView.
        if(slot!=1 || disposed || !texture.isAvailable() || width<=0 || height<=0)return;
        long now=android.os.SystemClock.uptimeMillis();
        if(backdrop==null || now-backdropAt>=100){
            int w=Math.min(480,width),h=Math.max(1,Math.round(height*w/(float)width));
            if(backdrop==null || backdrop.getWidth()!=w || backdrop.getHeight()!=h){releaseBackdrop();backdrop=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}
            texture.getBitmap(backdrop);backdropAt=now;
        }
        if(backdrop!=null)canvas.drawBitmap(backdrop,null,new Rect(0,0,getWidth(),getHeight()),backdropPaint);
    }
    private void releaseBackdrop(){backdrop=null;backdropAt=0;}
    public void ready(int id){failed=false;message.setVisibility(GONE);texture.setAlpha(0);texture.animate().alpha(1).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();if(!recorded){recorded=true;launched.run();}}
    public void error(String value){failed=true;message.setText(value+"\n请使用目录按钮重新选择应用");message.setVisibility(VISIBLE);message.setBackgroundColor(0xE60B1221);}
    public void state(JSONObject state){} // progress UI was deliberately removed; host diagnostics remain available
    public void seekResult(boolean ok){}
    private int density(){return Math.max(120,Math.min(640,getResources().getDisplayMetrics().densityDpi));}
    private int dp(float value){return SpaceStyle.dp(getContext(),value);}
}
