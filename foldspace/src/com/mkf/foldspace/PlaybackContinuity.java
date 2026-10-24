package com.mkf.foldspace;

/** Never rewind an advancing video, nor seek for tiny rounding differences or completed clips. */
public final class PlaybackContinuity {
    public static boolean needsRestore(float before,float after){
        return Float.isFinite(before) && Float.isFinite(after) && before>=0 && before<.98f && after>=0 && after<=1 && before-after>.035f;
    }
    private PlaybackContinuity(){}
}
