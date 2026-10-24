package com.mkf.foldspace;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Live upper-screen app surface plus lower-screen controls. No fullscreen fallback. */
public final class AppHostView extends FrameLayout implements TextureView.SurfaceTextureListener,AppHostController.Listener {
    private final TextureView texture;
    private final TextView message;
    private final AppCatalog.App app;
    private final Runnable launched;
    private AppHostController controller;
    private Surface surface;
    private TextView time,calibrate;
    private SeekBar progress;
    private int width,height,calibrationStep;
    private float[] line;
    private boolean disposed,dragging,recorded;
    private long duration;
    private String mode="none";
    private final int light=0xFFF0F5FF,muted=0xFFB6C6E0;
    public AppHostView(Context c,AppCatalog.App app,Runnable launched){
        super(c);this.app=app;this.launched=launched;setBackgroundColor(0xFF0B1221);
        texture=new TextureView(c);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));
        message=label("正在连接 Shizuku，准备上半屏应用…",14,light);message.setGravity(Gravity.CENTER);message.setPadding(dp(20),dp(12),dp(20),dp(12));addView(message,new LayoutParams(-1,-1));
        texture.setOnTouchListener((v,event)->{
            if(calibrationStep!=0){
                if(event.getActionMasked()==MotionEvent.ACTION_UP){
                    if(line==null)line=new float[4];int offset=calibrationStep==1?0:2;line[offset]=event.getX()/Math.max(1,width);line[offset+1]=event.getY()/Math.max(1,height);
                    if(calibrationStep==1){calibrationStep=2;updateCalibrationButton();}
                    else{calibrationStep=0;updateCalibrationButton();if(Math.abs(line[2]-line[0])<.2f){line=null;notifyUser("两端距离太近，请重新校准");}else{if(line[0]>line[2]){float x=line[0],y=line[1];line[0]=line[2];line[1]=line[3];line[2]=x;line[3]=y;}saveCalibration();notifyUser("进度条已校准");if(progress!=null)progress.setEnabled(true);}}
                }return true;
            }
            if(controller!=null)controller.touch(event);return true;
        });
    }
    public View controls(Runnable directory){
        LinearLayout box=column();box.setPadding(0,dp(6),0,0);
        LinearLayout bar=new LinearLayout(getContext());bar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        TextView back=button("返回",dp(62));back.setContentDescription("返回上方应用；长按返回视频目录");back.setOnClickListener(v->{if(controller!=null)controller.back();});back.setOnLongClickListener(v->{directory.run();return true;});bar.addView(back);
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(38)));
        progress=new SeekBar(getContext());progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFB1CAFF));progress.setThumbTintList(android.content.res.ColorStateList.valueOf(light));progress.setEnabled(false);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)time.setText(duration>0?format((long)(value/1000f*duration))+" / "+format(duration):"目标位置 "+Math.round(value/10f)+"%");}
            public void onStopTrackingTouch(SeekBar bar){dragging=false;if(controller!=null)controller.seek(bar.getProgress()/1000f,line);}
        });box.addView(progress,new LinearLayout.LayoutParams(-1,dp(39)));
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        time=label("等待上方视频提供进度",11,muted);row.addView(time,new LinearLayout.LayoutParams(0,dp(35),1));
        calibrate=button("校准进度条",dp(106));calibrate.setOnClickListener(v->{calibrationStep=1;updateCalibrationButton();notifyUser("请点上方原生进度条的左端");});
        calibrate.setOnLongClickListener(v->{line=null;calibrationStep=0;updateCalibrationButton();AppCatalog.prefs(getContext()).edit().remove(key("ratio")).apply();if(progress!=null)progress.setEnabled(!mode.equals("none"));notifyUser("已清除进度条校准");return true;});row.addView(calibrate);box.addView(row,new LinearLayout.LayoutParams(-1,dp(39)));
        TextView pad=label(app.packageName.contains("aweme")?"↑\n上下滑动  切换视频\n↓":"触控区\n手势同步到上半屏",18,light);pad.setGravity(Gravity.CENTER);pad.setLineSpacing(dp(9),1);pad.setBackground(glass());
        pad.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(true);if(controller!=null && width>0 && height>0){MotionEvent mapped=MotionEvent.obtain(event);Matrix transform=new Matrix();transform.setScale(width/(float)Math.max(1,v.getWidth()),height/(float)Math.max(1,v.getHeight()));mapped.transform(transform);controller.touch(mapped);mapped.recycle();}return true;});
        LinearLayout.LayoutParams padParams=new LinearLayout.LayoutParams(-1,0,1);padParams.topMargin=dp(10);box.addView(pad,padParams);
        TextView note=label("触控区轻点和滑动均传递到上方应用",10,muted);note.setGravity(Gravity.CENTER);box.addView(note,new LinearLayout.LayoutParams(-1,dp(26)));
        return box;
    }
    public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
        if(disposed)return;width=w;height=h;surface=new Surface(st);loadCalibration();controller=new AppHostController(getContext(),app,this);
        controller.prepare(surface,w,h,density());
    }
    public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){width=w;height=h;loadCalibration();if(controller!=null)controller.resize(w,h,density());}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st){if(controller!=null){controller.dispose();controller=null;}if(surface!=null){surface.release();surface=null;}return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture st){}
    public void dispose(){disposed=true;texture.animate().cancel();if(controller!=null){controller.dispose();controller=null;}if(surface!=null){surface.release();surface=null;}}
    public void ready(int id){message.setVisibility(GONE);texture.setAlpha(0);texture.animate().alpha(1f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();if(!recorded){recorded=true;launched.run();}}
    public void error(String error){message.setText(error+"\n长按下方“返回”可重新选择视频应用");message.setVisibility(VISIBLE);message.setBackgroundColor(0xE60B1221);if(progress!=null)progress.setEnabled(false);}
    public void state(JSONObject state){
        mode=state.optString("mode","none");duration=state.optLong("duration",0);boolean enabled=!mode.equals("none") || line!=null;
        if(progress!=null){progress.setEnabled(enabled && calibrationStep==0);if(!dragging){if(duration>0){long pos=state.optLong("position",0);progress.setProgress((int)(pos*1000/Math.max(1,duration)));time.setText(format(pos)+" / "+format(duration));}else if(state.has("fraction")){progress.setProgress((int)(state.optDouble("fraction",0)*1000));time.setText("原生进度 "+Math.round(progress.getProgress()/10f)+"%");}else time.setText(enabled?"原生进度映射 · 时间见上方视频":"该内容尚未提供可控制进度");}}
    }
    public void seekResult(boolean ok){if(!ok)notifyUser("进度控制未成功，请显示原生进度条后重新校准");}
    private void updateCalibrationButton(){if(calibrate!=null)calibrate.setText(calibrationStep==1?"请点左端":calibrationStep==2?"请点右端":"校准进度条");}
    private void notifyUser(String text){Toast.makeText(getContext(),text,Toast.LENGTH_SHORT).show();}
    private String key(String suffix){return "seek."+app.packageName+"."+suffix;}
    private void saveCalibration(){SharedPreferences.Editor editor=AppCatalog.prefs(getContext()).edit();for(int i=0;i<4;i++)editor.putFloat(key("p"+i),line[i]);editor.putFloat(key("ratio"),width/(float)Math.max(1,height)).apply();}
    private void loadCalibration(){SharedPreferences p=AppCatalog.prefs(getContext());float ratio=p.getFloat(key("ratio"),0);if(ratio==0 || Math.abs(ratio-width/(float)Math.max(1,height))>.08f){line=null;return;}float[] saved=new float[4];for(int i=0;i<4;i++)saved[i]=p.getFloat(key("p"+i),Float.NaN);for(float f:saved)if(Float.isNaN(f)){line=null;return;}line=saved;}
    private int density(){return Math.max(120,Math.min(640,getResources().getDisplayMetrics().densityDpi));}
    private static String format(long ms){long seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView t=new TextView(getContext());t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView button(String text,int width){TextView t=label(text,12,light);t.setGravity(Gravity.CENTER);t.setBackground(glass());t.setLayoutParams(new LinearLayout.LayoutParams(width,dp(36)));return t;}
    private GradientDrawable glass(){GradientDrawable g=new GradientDrawable();g.setColor(0x2C7188B5);g.setCornerRadius(dp(18));g.setStroke(dp(1),0x30CADBFF);return g;}
    private int dp(float d){return Math.round(d*getResources().getDisplayMetrics().density);}
}
