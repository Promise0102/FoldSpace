package com.mkf.foldspace;

public final class PhysicalOrientationTest {
    private static int checks;
    private static void check(int width,int height,int rotation,boolean expected){checks++;if(PhysicalOrientation.landscape(width,height,rotation)!=expected)throw new AssertionError(width+"x"+height+" rotation="+rotation);}
    public static void main(String[] args){
        // Inner landscape-natural panel and portrait-natural cover panel need opposite parity.
        check(1920,1792,0,true);check(1920,1792,1,false);check(1920,1792,2,true);check(1920,1792,3,false);
        check(1080,2120,0,false);check(1080,2120,1,true);check(1080,2120,2,false);check(1080,2120,3,true);
        for(int rotation=0;rotation<4;rotation++)check(1080,1080,rotation,false);
        check(0,1792,0,false);check(1920,-1,0,false);check(1920,1792,4,false);
        System.out.println("PASS: "+checks+" natural-panel and physical-orientation assertions");
    }
}
