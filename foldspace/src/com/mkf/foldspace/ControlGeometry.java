package com.mkf.foldspace;

/** Seek coordinates are always finite, normalized, and confined to the hosted display. */
public final class ControlGeometry {
    public static float[] point(float fraction,float[] line,int width,int height){
        if(!finite(fraction) || line==null || line.length!=4 || width<=0 || height<=0)return null;
        for(float f:line)if(!finite(f) || f<0 || f>1)return null;
        if(line[2]-line[0]<.2f)return null;
        fraction=Math.max(0,Math.min(1,fraction));
        float x=(line[0]+fraction*(line[2]-line[0]))*width,y=(line[1]+fraction*(line[3]-line[1]))*height;
        return new float[]{Math.max(0,Math.min(width-1,x)),Math.max(0,Math.min(height-1,y))};
    }
    private static boolean finite(float f){return !Float.isNaN(f) && !Float.isInfinite(f);}
    private ControlGeometry(){}
}
