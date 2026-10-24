package com.mkf.foldspace;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class FoldMonitor implements SensorEventListener {
    public interface Listener { void onFold(float angle, boolean hovering, boolean sensorAvailable); }
    private final SensorManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private Sensor hinge;
    private boolean registered;
    private final FoldPolicy.Tracker policy=new FoldPolicy.Tracker();
    private float angle = Float.NaN;
    private final Runnable commit = this::commitCandidate;
    private void commitCandidate() {
        if(!registered)return;
        if(policy.advance(android.os.SystemClock.elapsedRealtime())){
            Log.i("FoldSpaceSensor", "posture=" + policy.hovering() + " angle=" + angle);
            listener.onFold(angle,policy.hovering(),true);
        }
        schedule();
    }
    private void schedule(){handler.removeCallbacks(commit);long delay=policy.delay(android.os.SystemClock.elapsedRealtime());if(registered && delay>=0)handler.postDelayed(commit,Math.max(1,delay));}

    public FoldMonitor(Context context, Listener listener) {
        this.manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;
    }
    public void start() {
        if (registered) return;
        hinge = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, true);
        if (hinge == null) hinge = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        if (hinge == null) hinge = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, false);
        try { registered = hinge != null && manager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_NORMAL, handler); }
        catch (SecurityException ex) { Log.w("FoldSpaceSensor", "Hinge access unavailable", ex); }
        Log.i("FoldSpaceSensor", "registered=" + registered + " sensor=" + (hinge == null ? "none" : hinge.getName()));
        listener.onFold(angle,policy.hovering(),registered);
    }
    public void stop() {
        registered = false; handler.removeCallbacks(commit);policy.reset();angle=Float.NaN;manager.unregisterListener(this);
    }
    public float angle() { return angle; }
    public boolean hovering() { return policy.hovering(); }
    public boolean available() { return registered; }
    @Override public void onSensorChanged(SensorEvent event) {
        if(event.values.length==0)return;angle=event.values[0];
        if(policy.accept(angle,android.os.SystemClock.elapsedRealtime()))Log.i("FoldSpaceSensor","posture="+policy.hovering()+" angle="+angle);
        schedule();listener.onFold(angle,policy.hovering(),registered);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
