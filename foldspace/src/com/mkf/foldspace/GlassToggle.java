package com.mkf.foldspace;

import android.content.Context;
import android.graphics.*;
import android.widget.CompoundButton;

final class GlassToggle extends CompoundButton {
    private final LiquidGlassDrawable glass;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    GlassToggle(Context context){super(context);setButtonDrawable(null);setClickable(true);setFocusable(true);setMinHeight(SpaceStyle.dp(context,48));glass=new LiquidGlassDrawable(this,18);SpaceStyle.press(this);}
    @Override public void draw(Canvas canvas){if(!LiquidGlassDrawable.isCapturing())super.draw(canvas);}
    @Override protected void onDraw(Canvas canvas){
        int pad=SpaceStyle.dp(getContext(),4),height=SpaceStyle.dp(getContext(),30),top=(getHeight()-height)/2;
        glass.setBounds(0,top,getWidth(),top+height);glass.draw(canvas);
        paint.setColor(isChecked()?0xAA76AEFF:0x40506583);canvas.drawRoundRect(1,top+1,getWidth()-1,top+height-1,height/2f,height/2f,paint);
        float radius=(height-pad*2)/2f,x=isChecked()?getWidth()-pad-radius:pad+radius;
        paint.setShader(new LinearGradient(0,top,0,top+height,new int[]{0xFFFFFFFF,0xFFB7D7FF},null,Shader.TileMode.CLAMP));canvas.drawCircle(x,top+height/2f,radius,paint);paint.setShader(null);
    }
    @Override public void setChecked(boolean checked){super.setChecked(checked);invalidate();}
}
