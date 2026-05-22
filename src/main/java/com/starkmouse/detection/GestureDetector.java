package com.starkmouse.detection;

import org.opencv.core.Mat;

public interface GestureDetector {
    Gesture detect(Mat frame);

    String getName();

    default void calibrate() {
    }
}
