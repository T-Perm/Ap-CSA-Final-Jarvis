package com.starkmouse.detection;

/**
 * JNI wrapper for the C++ MediaPipe gesture tracker.
 * Loads the stark_mouse_jni library and handles frames and landmarks callbacks.
 */
public class NativeGestureDetector {

    /**
     * Interface representing a callback listener for received frames and landmarks.
     */
    public interface FrameListener {
        /**
         * Invoked when a frame and its landmarks are detected by the tracker.
         *
         * @param jpegBytes the JPEG compressed image frame bytes
         * @param landmarks the 63 float coordinates representing 21 landmarks
         */
        void onFrameAndLandmarks(byte[] jpegBytes, double[] landmarks);
    }

    /**
     * Listener interface to receive callback data.
     */
    private FrameListener listener;

    /**
     * Constructs a NativeGestureDetector with the specified frame listener.
     *
     * @param listener the listener instance to notify on tracking frames
     */
    public NativeGestureDetector(FrameListener listener) {
        this.listener = listener;
    }

    static {
        String dllPath = System.getProperty("user.dir")
                + java.io.File.separator + "stark_mouse_jni.dll";
        System.load(dllPath);
    }

    /**
     * Callback method invoked from C++ native side to send frames and landmarks.
     *
     * @param jpegBytes raw JPEG frame data
     * @param landmarks hand landmark points
     */
    public void onFrameAndLandmarksDetected(byte[] jpegBytes, double[] landmarks) {
        if (listener != null) {
            listener.onFrameAndLandmarks(jpegBytes, landmarks);
        }
    }

    /**
     * Starts the native webcam and hand tracker loop in a separate C++ thread.
     */
    public native void startTracker();

    /**
     * Stops the native webcam and joins the tracker thread in C++.
     */
    public native void stopTracker();
}
