package com.starkmouse.app;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

/** Throwaway: opens webcam, saves one frame. Delete after this chapter. */
public class OpenCvTest {
    public static void main(String[] args) throws InterruptedException {
        OpenCV.loadLocally();

        VideoCapture cap = new VideoCapture(0);
        if (!cap.isOpened()) {
            System.err.println("Could not open camera 0");
            return;
        }

        Mat frame = new Mat();
        for (int i = 0; i < 10; i++) {
            cap.read(frame);
            Thread.sleep(50);
        }

        if (frame.empty()) {
            System.err.println("Got empty frame");
        } else {
            System.out.println("Frame: " + frame.size());
            Imgcodecs.imwrite("test-frame.png", frame);
            System.out.println("Saved test-frame.png");
        }

        cap.release();
        frame.release();
    }
}