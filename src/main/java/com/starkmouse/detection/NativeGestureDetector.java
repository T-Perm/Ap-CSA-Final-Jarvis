package com.starkmouse.detection;

public class NativeGestureDetector {

    public interface FrameListener {
        void onFrameAndLandmarks(byte[] jpegBytes, double[] landmarks);
    }

    private FrameListener listener;

    public NativeGestureDetector(FrameListener listener) {
        this.listener = listener;
    }

    static {
        String dllPath = System.getProperty("user.dir")
                + java.io.File.separator + "stark_mouse_jni.dll";
        System.load(dllPath);
    }

    public void onFrameAndLandmarksDetected(byte[] jpegBytes, double[] landmarks) {
        if (listener != null) {
            listener.onFrameAndLandmarks(jpegBytes, landmarks);
        }
    }

    public native void startTracker();

    public native void stopTracker();
}
