package com.mkf.foldspace;

/** One horizontal gesture commits at most one application switch; vertical/multi-touch stays native. */
final class UpperAppSwipe {
    private final float slop,threshold;
    private float startX,startY;
    private int mode; // 0 ended, 1 undecided, 2 horizontal, 3 native
    UpperAppSwipe(float slop,float threshold){this.slop=slop;this.threshold=threshold;}
    void start(float x,float y){startX=x;startY=y;mode=1;}
    boolean move(float x,float y,int pointers){
        if(mode==0 || mode==3)return false;
        if(pointers!=1 || !Float.isFinite(x) || !Float.isFinite(y)){mode=3;return false;}
        float dx=Math.abs(x-startX),dy=Math.abs(y-startY);
        if(mode==1){if(dx>slop && dx>dy*1.35f)mode=2;else if(dy>slop)mode=3;}
        return mode==2;
    }
    int finish(float x,float y){
        boolean horizontal=move(x,y,1);float dx=x-startX,dy=y-startY;mode=0;
        return horizontal && Math.abs(dx)>=threshold && Math.abs(dx)>Math.abs(dy)*1.35f?(dx<0?1:-1):0;
    }
    void cancel(){mode=0;}
    float distance(float x){return x-startX;}
}
