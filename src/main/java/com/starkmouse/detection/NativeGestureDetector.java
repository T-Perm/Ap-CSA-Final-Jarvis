package com.starkmouse.detection;

public class NativeGestureDetector {

    public interface GestureListener {
        void onGesture(Gesture gesture);
    }

    private GestureListener listener;

    public NativeGestureDetector(GestureListener listener) {
        this.listener = listener;
    }

    static {
        String dllPath = System.getProperty("user.dir")
                + java.io.File.separator + "stark_mouse_jni.dll";
        System.load(dllPath);
    }

    public void onGestureDetected(String typeStr, double x, double y, double confidence) {
        if (listener != null) {
            Gesture.Type type = Gesture.Type.valueOf(typeStr);
            Gesture g = new Gesture(type, x, y, confidence);
            listener.onGesture(g);
        }
    }

    public native void startTracker();

    public native void stopTracker();
}
