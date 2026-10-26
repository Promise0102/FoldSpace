package com.mkf.foldspace;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;

final class GlassButton extends TextView {
    GlassButton(Context context){super(context);setBackground(new LiquidGlassDrawable(this,24));SpaceStyle.press(this);}
    @Override public void draw(Canvas canvas){if(!LiquidGlassDrawable.isCapturing())super.draw(canvas);}
}
