package com.mkf.foldspace;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** Fullscreen opaque orientation owner, launched only by an explicit landscape-button tap. */
public final class RotationActivity extends Activity {
    private HoverRotation owner;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ImmersiveMode.apply(getWindow());
        owner=HoverRotation.owner(getIntent().getStringExtra("rotation_owner"));
        if(owner==null || owner.isClosed()){finishAndRemoveTask();return;}
        View background=new View(this);background.setBackgroundColor(0xFF17223B);setContentView(background);
        owner.attachHelper(this);
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)ImmersiveMode.apply(getWindow());}
    @Override protected void onStop(){super.onStop();if(owner!=null && !isChangingConfigurations())owner.helperStopped(this);}
    @Override protected void onDestroy(){if(owner!=null)owner.helperStopped(this);owner=null;super.onDestroy();}
}
