package com.mkf.foldspace;

public final class PlaybackContinuityTest {
    private static int checks;
    private static void check(float before,float after,boolean expected){checks++;if(PlaybackContinuity.needsRestore(before,after)!=expected)throw new AssertionError(before+" -> "+after);}
    public static void main(String[] args){
        check(.5f,0,true);check(.7f,.1f,true);check(.2f,.19f,false);check(.5f,.6f,false);check(0,0,false);
        check(.99f,0,false);check(1,0,false);check(Float.NaN,0,false);check(.5f,Float.NaN,false);check(Float.POSITIVE_INFINITY,0,false);
        check(-1,0,false);check(.5f,-1,false);check(.5f,2,false);check(2,0,false);check(.8f,.8f,false);
        System.out.println("PASS: "+checks+" playback continuity assertions");
    }
}
