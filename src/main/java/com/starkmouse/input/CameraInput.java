package com.starkmouse.input;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class CameraInput {
    private VideoCapture capture;
    private final int cameraIndex;
    private final Mat buffer = new Mat();

    public CameraInput(int cameraIndex) {
        this.cameraIndex = cameraIndex;
    }

    public void start() {
        capture = new VideoCapture(cameraIndex);
        if (!capture.isOpened()) {
            throw new RuntimeException("Could not open camera " + cameraIndex);
        }
        for (int i = 0; i < 10; i++) {
            capture.read(buffer);

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException("Camera warm-up interrupted", e);
            }
        }
    }

    public Mat grabFrame() {
        if (capture == null || !capture.isOpened()) {
            return null;
        }

        boolean ok = capture.read(buffer);
        return (ok && !buffer.empty()) ? buffer : null;
    }

    public void stop() {
        if (capture != null) {
            capture.release();
            capture = null;
        }
    }

    public boolean isRunning() {
        return capture != null && capture.isOpened();
    }
}