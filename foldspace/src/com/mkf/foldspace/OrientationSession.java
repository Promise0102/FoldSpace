package com.mkf.foldspace;

/** A window request has no access to global auto-rotate settings and needs no timer. */
final class OrientationSession {
    static final int LANDSCAPE=0; // ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    interface Window {int requested();void request(int orientation);}
    private final Window window;
    private int previous;
    private boolean started,changed;
    OrientationSession(Window window){this.window=window;}
    void start(){
        if(started)return;
        previous=window.requested();changed=previous!=LANDSCAPE;
        if(changed)window.request(LANDSCAPE);
        started=true;
    }
    void close(){
        if(!started)return;started=false;
        // Preserve a more recent explicit request made by another part of the same window.
        if(changed && window.requested()==LANDSCAPE)window.request(previous);
    }
}
