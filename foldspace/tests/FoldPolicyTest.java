package com.mkf.foldspace;

/** Deterministic clock tests for continuous entry and exit, including sensor interruption. */
public final class FoldPolicyTest {
    private static int checks;
    private static void expect(float angle, boolean previous, boolean expected) {
        checks++;
        if(FoldPolicy.candidate(angle,previous)!=expected) throw new AssertionError("angle="+angle+", previous="+previous+", expected="+expected);
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        for(boolean prior:new boolean[]{false,true}){
            expect(Float.NaN,prior,false);expect(Float.POSITIVE_INFINITY,prior,false);expect(-1,prior,false);expect(181,prior,false);
            expect(0,prior,false);expect(109.99f,prior,false);expect(110,prior,true);expect(125,prior,true);expect(140,prior,true);expect(140.01f,prior,false);expect(180,prior,false);
        }
        FoldPolicy.Tracker p=new FoldPolicy.Tracker();
        check(!p.accept(125,0),"entry must wait");check(p.delay(0)==3000,"full entry dwell");check(!p.advance(2999) && !p.hovering(),"not at 2999ms");
        check(p.advance(3000) && p.hovering(),"enter exactly at 3000ms, even without another sensor event");check(!p.advance(4000),"no duplicate entry");
        check(!p.accept(109,5000),"exit must wait");check(!p.advance(7999) && p.hovering(),"not before full exit dwell");check(p.advance(8000) && !p.hovering(),"exit exactly at 3000ms");
        p.reset();p.accept(110,10000);p.accept(140,11000);p.accept(120,12000);
        check(p.delay(12000)==1000,"movement within range must preserve original dwell");check(p.advance(13000) && p.hovering(),"inclusive range enters after continuous dwell");
        p.accept(150,14000);p.accept(90,15000);p.accept(0,16000);
        check(p.delay(16000)==1000,"movement outside range must preserve exit dwell");check(p.advance(17000) && !p.hovering(),"all outside positions count toward exit");
        p.reset();p.accept(125,0);p.accept(109,2999);p.accept(125,3000);
        check(!p.advance(5999),"a brief boundary crossing resets entry clock");check(p.advance(6000) && p.hovering(),"fresh full dwell enters");
        p.accept(141,7000);p.accept(125,9999);
        check(p.delay(10000)==-1,"returning inside cancels pending exit");check(!p.advance(12000) && p.hovering(),"brief outside excursion does not exit");
        p.accept(141,13000);p.accept(140,14000);p.accept(141,15000);
        check(!p.advance(17999) && p.hovering(),"exit needs a fresh uninterrupted dwell");check(p.advance(18000) && !p.hovering(),"new exit dwell completes");
        p.reset();p.accept(130,0);p.accept(Float.NaN,2999);
        check(!p.advance(9000) && !p.hovering(),"invalid readings cannot complete entry");p.accept(130,9000);check(p.advance(12000),"valid dwell after invalid reading works");
        p.accept(100,13000);p.accept(Float.NaN,14000);check(!p.advance(20000) && p.hovering(),"unknown sensor state does not force exit");
        p.accept(100,20000);check(p.advance(23000) && !p.hovering(),"exit resumes with a new full dwell");
        p.accept(120,30000);check(p.accept(130,33000) && p.hovering(),"later sensor update can commit elapsed dwell");
        p.accept(180,34000);p.reset();check(!p.advance(50000) && !p.hovering() && p.delay(50000)==-1,"stop clears pending work and state");
        System.out.println("PASS: "+checks+" range and continuous-dwell assertions");
    }
}
