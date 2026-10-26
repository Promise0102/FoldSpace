package com.mkf.foldspace;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.TextView;

/** Shared colors, radii and controls for the directory, player and lower-half settings. */
final class SpaceStyle {
    static final int WHITE=0xFFF4F7FF, MUTED=0xFFB5C3DB, ACCENT=0xFFB1CAFF;
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String value,float size,int color){
        TextView view=new TextView(c);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setIncludeFontPadding(false);return view;
    }
    static Drawable glass(Context c,int radius,int color){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(c,radius));shape.setStroke(dp(c,1),0x26D8E4FF);
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),shape,null);
    }
    static Drawable tile(Context c){
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x4A8298BE,0x24445E91});
        shape.setCornerRadius(dp(c,24));shape.setStroke(dp(c,1),0x27D8E4FF);
        return new RippleDrawable(ColorStateList.valueOf(0x26FFFFFF),shape,null);
    }
    static TextView button(Context c,String label){
        TextView view=new GlassButton(c);view.setText(label);view.setTextSize(13);view.setTextColor(WHITE);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER);view.setClickable(true);view.setFocusable(true);
        view.setMinHeight(dp(c,48));return view;
    }
    static void press(android.view.View view){
        view.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN){v.animate().cancel();v.animate().scaleX(.965f).scaleY(.94f).setDuration(110).start();}
            else if(action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL){v.animate().cancel();v.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(.8f)).start();}
            return false;
        });
    }
    private SpaceStyle(){}
}
