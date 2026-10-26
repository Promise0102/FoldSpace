package com.mkf.foldspace;

public final class UpperAppSwipeTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Swipe check "+checks);}
    public static void main(String[] args){
        UpperAppSwipe swipe=new UpperAppSwipe(10,60);
        swipe.start(200,100);check(!swipe.move(196,101,1));check(swipe.move(150,102,1));check(swipe.move(50,103,1));check(swipe.finish(30,102)==1);check(swipe.finish(0,100)==0);
        swipe.start(100,100);check(swipe.move(180,102,1));check(swipe.finish(220,104)==-1);
        swipe.start(100,100);check(swipe.finish(100,100)==0);
        swipe.start(100,100);check(swipe.move(120,101,1));check(swipe.finish(140,103)==0);
        swipe.start(100,100);check(!swipe.move(103,150,1));check(!swipe.move(250,153,1));check(swipe.finish(300,160)==0);
        swipe.start(100,100);check(!swipe.move(120,103,2));check(swipe.finish(250,103)==0);
        swipe.start(100,100);check(swipe.move(180,103,1));swipe.cancel();check(swipe.finish(300,104)==0);
        swipe.start(100,100);check(swipe.move(170,101,1));check(!swipe.move(180,102,2));check(swipe.finish(250,103)==0);
        swipe.start(100,100);check(swipe.finish(10,100)==1);check(swipe.finish(-100,100)==0);
        swipe.start(100,100);check(!swipe.move(Float.NaN,100,1));check(swipe.finish(250,103)==0);
        // A long drag has one commit, regardless of how many move samples arrive.
        swipe.start(1000,100);for(int x=990;x>=0;x-=10)swipe.move(x,100,1);check(swipe.finish(0,100)==1);check(swipe.finish(-100,100)==0);
        System.out.println("PASS: "+checks+" one-application-per-swipe and native-gesture assertions");
    }
}
