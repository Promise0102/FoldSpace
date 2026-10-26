package com.mkf.foldspace;

public final class OrientationSessionTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Window rotation check "+checks);}
    private static final class Window implements OrientationSession.Window {
        int orientation,writes;boolean fail;
        Window(int orientation){this.orientation=orientation;}
        public int requested(){return orientation;}
        public void request(int value){if(fail)throw new IllegalStateException("Window unavailable");orientation=value;writes++;}
    }
    public static void main(String[] args){
        // Initially unspecified, portrait, reverse landscape, sensor and user modes.
        for(int initial:new int[]{-1,1,8,4,13}){
            Window window=new Window(initial);OrientationSession session=new OrientationSession(window);
            session.close();check(window.writes==0);
            session.start();check(window.orientation==0);check(window.writes==1);
            session.start();check(window.writes==1);
            session.close();check(window.orientation==initial);check(window.writes==2);
            session.close();check(window.writes==2);
            session.start();session.close();check(window.orientation==initial);check(window.writes==4);
        }
        Window already=new Window(0);OrientationSession same=new OrientationSession(already);same.start();same.close();check(already.writes==0);check(already.orientation==0);
        Window changed=new Window(-1);OrientationSession own=new OrientationSession(changed);own.start();changed.request(1);own.close();check(changed.orientation==1);check(changed.writes==2);
        Window lost=new Window(-1);lost.fail=true;OrientationSession failed=new OrientationSession(lost);
        try{failed.start();throw new AssertionError("Failure not propagated");}catch(IllegalStateException expected){checks++;}
        lost.fail=false;failed.close();check(lost.writes==0);failed.start();failed.close();check(lost.orientation==-1);
        Window upper=new Window(-1),lower=new Window(1);OrientationSession first=new OrientationSession(upper),second=new OrientationSession(lower);
        first.start();second.start();first.close();check(upper.orientation==-1);check(lower.orientation==0);second.close();check(lower.orientation==1);
        System.out.println("PASS: "+checks+" window-scoped rotation and restoration assertions");
    }
}
