package com.mkf.foldspace;

public final class ControlGeometryTest {
    private static int checks;
    private static void check(boolean condition,String description){checks++;if(!condition)throw new AssertionError(description);}
    private static void near(float actual,float expected,String name){check(Math.abs(actual-expected)<.01f,name+": "+actual+" != "+expected);}
    public static void main(String[] args){
        float[] line={.1f,.9f,.9f,.9f};
        float[] start=ControlGeometry.point(0,line,1000,500),middle=ControlGeometry.point(.5f,line,1000,500),end=ControlGeometry.point(1,line,1000,500);
        near(start[0],100,"left endpoint");near(middle[0],500,"halfway seek");near(end[0],900,"right endpoint");near(middle[1],450,"native track y");
        float[] resized=ControlGeometry.point(.5f,line,1792,840);near(resized[0],896,"resized x");near(resized[1],756,"resized y");
        float[] fullWidth={0,1,1,1};float[] edge=ControlGeometry.point(1,fullWidth,1792,840);near(edge[0],1791,"100 percent remains inside display");near(edge[1],839,"bottom remains inside display");
        near(ControlGeometry.point(-2,line,1000,500)[0],100,"negative seek clamps to start");near(ControlGeometry.point(2,line,1000,500)[0],900,"overshoot clamps to end");
        check(ControlGeometry.point(Float.NaN,line,1000,500)==null,"NaN seek rejected");check(ControlGeometry.point(Float.POSITIVE_INFINITY,line,1000,500)==null,"infinite seek rejected");
        check(ControlGeometry.point(.5f,null,1000,500)==null,"uncalibrated progress cannot inject a guess");check(ControlGeometry.point(.5f,new float[]{0,1},1000,500)==null,"incomplete calibration rejected");
        check(ControlGeometry.point(.5f,new float[]{Float.NaN,0,1,0},1000,500)==null,"NaN calibration rejected");
        check(ControlGeometry.point(.5f,new float[]{0,-.1f,1,0},1000,500)==null,"off-display calibration rejected");
        check(ControlGeometry.point(.5f,new float[]{0,0,1.1f,0},1000,500)==null,"overrun calibration rejected");
        check(ControlGeometry.point(.5f,new float[]{.9f,.5f,.1f,.5f},1000,500)==null,"backwards line must be normalized first");
        check(ControlGeometry.point(.5f,new float[]{.4f,.5f,.5f,.5f},1000,500)==null,"short accidental calibration rejected");
        check(ControlGeometry.point(.5f,line,0,500)==null,"zero-width host rejected");check(ControlGeometry.point(.5f,line,1000,0)==null,"zero-height host rejected");
        // Native gesture coordinate scale: the same normalized line works across upper viewport sizes.
        for(int w:new int[]{360,1080,1792,1920,2560})for(int h:new int[]{240,540,840,960})for(int i=0;i<=100;i++){
            float[] p=ControlGeometry.point(i/100f,line,w,h);check(p!=null && p[0]>=0 && p[0]<w && p[1]>=0 && p[1]<h,"every supported target is bounded");
        }
        System.out.println("PASS: "+checks+" seek geometry and calibration assertions");
    }
}
