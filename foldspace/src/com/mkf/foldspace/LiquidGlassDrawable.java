package com.mkf.foldspace;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.WeakHashMap;

/**
 * Native adapter for QmDeve/AndroidLiquidGlassView's MIT AGSL shader and RenderNode pipeline.
 * Copyright (c) 2025-2026 Donny Yang; full license in AndroidLiquidGlassView-MIT-LICENSE.txt.
 * Upstream commit: d4238c557e46271f29ec0e3d3ee211bcf58dc2e0. Shader kept unmodified.
 */
final class LiquidGlassDrawable extends Drawable {
    private static final WeakHashMap<View,Backdrop> BACKDROPS=new WeakHashMap<>();
    private static boolean capturing;
    private final View host;
    private final float corner;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path clip=new Path();
    private Api33 renderer;
    private boolean unsupported;
    private float touchX=.3f,touchY=.2f;
    private int opacity=255;
    LiquidGlassDrawable(View host,float radius){this.host=host;corner=SpaceStyle.dp(host.getContext(),radius);}
    static boolean isCapturing(){return capturing;}
    @Override public void draw(Canvas canvas){
        if(capturing || getBounds().isEmpty())return;
        rect.set(getBounds());float radius=Math.min(corner,Math.min(rect.width(),rect.height())/2f);
        clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);
        boolean drawn=false;
        if(Build.VERSION.SDK_INT>=33 && canvas.isHardwareAccelerated() && !unsupported){
            try{
                if(renderer==null)renderer=new Api33(host);
                View root=host;while(root.getParent() instanceof View){root=(View)root.getParent();if(root instanceof SpacePanel)break;}
                Backdrop backdrop=BACKDROPS.get(root);if(backdrop==null){backdrop=new Backdrop();BACKDROPS.put(root,backdrop);}backdrop.record(root);
                renderer.draw(canvas,backdrop.node,root,host,rect,radius,host.isPressed());drawn=true;
            }catch(RuntimeException error){unsupported=true;android.util.Log.w("FoldSpaceGlass","Using glass fallback on this renderer",error);}
        }
        if(!drawn){paint.setShader(new LinearGradient(0,0,rect.width(),rect.height(),new int[]{0xAA8BADD2,0x88526A9B,0xAA233A61},null,Shader.TileMode.CLAMP));paint.setAlpha(opacity);canvas.drawRect(rect,paint);paint.setShader(null);}
        paint.setAlpha(opacity);paint.setColor(host.isPressed()?0x3649BFFF:0x164C8BF2);canvas.drawRect(rect,paint);
        float highlight=Math.max(1,Math.max(rect.width(),rect.height())*.85f);
        paint.setAlpha(opacity);paint.setShader(new RadialGradient(rect.left+rect.width()*touchX,rect.top+rect.height()*touchY,highlight,new int[]{host.isPressed()?0x85FFFFFF:0x3DFFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));canvas.drawRect(rect,paint);paint.setShader(null);
        canvas.restoreToCount(save);
        rect.inset(SpaceStyle.dp(host.getContext(),1)*.5f,SpaceStyle.dp(host.getContext(),1)*.5f);
        paint.setAlpha(opacity);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,host.getResources().getDisplayMetrics().density*.65f));
        paint.setShader(new LinearGradient(0,rect.top,rect.width(),rect.bottom,new int[]{0xD4EFFFFF,0x24BDCCFF,0x8697BAEF},new float[]{0,.55f,1},Shader.TileMode.CLAMP));canvas.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setHotspot(float x,float y){touchX=Math.max(0,Math.min(1,x/Math.max(1,host.getWidth())));touchY=Math.max(0,Math.min(1,y/Math.max(1,host.getHeight())));invalidateSelf();}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    private static final class Backdrop {
        final RenderNode node=new RenderNode("FoldSpace shared glass backdrop");long frame=-1;
        void record(View root){
            long current=android.os.SystemClock.uptimeMillis()/50;
            if(frame==current && node.hasDisplayList() && node.getWidth()==root.getWidth() && node.getHeight()==root.getHeight())return;
            int width=root.getWidth(),height=root.getHeight();if(width<=0 || height<=0)throw new IllegalStateException("Backdrop not laid out");
            node.setPosition(0,0,width,height);Canvas recording=node.beginRecording(width,height);capturing=true;
            try{drawScene(recording,root);frame=current;}finally{capturing=false;node.endRecording();}
        }
        // Never re-enter root.draw(): doing so can poison cached child display lists or create a
        // RenderNode cycle. Record background layers explicitly, excluding interactive glass.
        private void drawScene(Canvas canvas,View view){
            if(view.getVisibility()!=View.VISIBLE || view.getAlpha()==0 || view instanceof GlassButton || view instanceof GlassLayout || view instanceof GlassToggle)return;
            int save=canvas.save();canvas.clipRect(0,0,view.getWidth(),view.getHeight());
            Drawable background=view.getBackground();if(background!=null && !(background instanceof LiquidGlassDrawable))background.draw(canvas);
            if(view instanceof AppHostView)((AppHostView)view).drawBackdrop(canvas);
            else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);int childSave=canvas.save();canvas.translate(child.getLeft()-view.getScrollX()+child.getTranslationX(),child.getTop()-view.getScrollY()+child.getTranslationY());drawScene(canvas,child);canvas.restoreToCount(childSave);
            }}
            canvas.restoreToCount(save);
        }
    }
    @android.annotation.TargetApi(33)
    private static final class Api33 {
        private static String shaderCode;
        private final RuntimeShader shader;
        private final RenderNode node=new RenderNode("FoldSpace refractive glass control");
        private final int[] hostPosition=new int[2],rootPosition=new int[2];
        private int lastWidth,lastHeight;private float lastRadius;private boolean lastPressed;
        Api33(View host){
            if(shaderCode==null)try(InputStream input=host.getResources().openRawResource(R.raw.liquidglass_effect);ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);shaderCode=new String(output.toByteArray(),StandardCharsets.UTF_8);
            }catch(IOException error){throw new IllegalStateException("Liquid glass shader unavailable",error);}
            shader=new RuntimeShader(shaderCode);
        }
        void draw(Canvas canvas,RenderNode backdrop,View root,View host,RectF bounds,float radius,boolean pressed){
            int width=(int)bounds.width(),height=(int)bounds.height();if(width<=0 || height<=0)return;
            node.setPosition(0,0,width,height);host.getLocationInWindow(hostPosition);root.getLocationInWindow(rootPosition);
            Canvas content=node.beginRecording(width,height);
            try{content.translate(-(hostPosition[0]-rootPosition[0]+bounds.left),-(hostPosition[1]-rootPosition[1]+bounds.top));content.drawRenderNode(backdrop);}finally{node.endRecording();}
            if(width!=lastWidth || height!=lastHeight || radius!=lastRadius || pressed!=lastPressed){
                float density=host.getResources().getDisplayMetrics().density;
                shader.setFloatUniform("size",width,height);shader.setFloatUniform("offset",0,0);shader.setFloatUniform("cornerRadii",radius,radius,radius,radius);
                shader.setFloatUniform("refractionHeight",Math.min(height*.46f,12*density));shader.setFloatUniform("refractionAmount",-(pressed?10:7)*density);
                shader.setFloatUniform("depthEffect",.22f);shader.setFloatUniform("chromaticAberration",.36f);
                shader.setFloatUniform("contrast",.04f);shader.setFloatUniform("whitePoint",.07f);shader.setFloatUniform("chromaMultiplier",1.10f);
                shader.setFloatUniform("tintColor",.30f,.44f,.66f);shader.setFloatUniform("tintAlpha",.28f);
                RenderEffect blur=RenderEffect.createBlurEffect(5*density,5*density,Shader.TileMode.CLAMP);
                node.setRenderEffect(RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader,"content"),blur));
                lastWidth=width;lastHeight=height;lastRadius=radius;lastPressed=pressed;
            }
            int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.drawRenderNode(node);canvas.restoreToCount(save);
        }
    }
}
