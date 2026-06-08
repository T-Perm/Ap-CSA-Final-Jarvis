package com.starkmouse.detection;



public interface GestureDetector {
    Gesture detect();

    String getName();

    default void calibrate() {
    }
}
