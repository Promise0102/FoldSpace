package com.mkf.foldspace;

/** User-defined inclusive range with symmetric, continuous three-second dwell. */
public final class FoldPolicy {
    public static final long HOLD_MS=3000;
    public static boolean valid(float angle){return !Float.isNaN(angle) && !Float.isInfinite(angle) && angle>=0 && angle<=180;}
    public static boolean candidate(float angle, boolean wasHovering) {
        return valid(angle) && angle>=110f && angle<=140f;
    }
    public static final class Tracker {
        private boolean hovering,pending,target;
        private long since;
        public boolean accept(float angle,long now){
            if(!valid(angle)){pending=false;return false;}
            boolean next=candidate(angle,hovering);
            if(next==hovering)pending=false;
            else if(!pending || target!=next){pending=true;target=next;since=now;}
            return advance(now);
        }
        public boolean advance(long now){if(!pending || now-since<HOLD_MS)return false;hovering=target;pending=false;return true;}
        public boolean hovering(){return hovering;}
        public long delay(long now){return pending?Math.max(0,HOLD_MS-(now-since)):-1;}
        public void reset(){hovering=false;pending=false;}
    }
    private FoldPolicy() {}
}
