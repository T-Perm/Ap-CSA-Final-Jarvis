package com.starkmouse.detection;

/**
 * Interface representing a gesture detector component.
 */
public interface GestureDetector {
    /**
     * Detects the current gesture from the tracking source.
     *
     * @return the detected Gesture object containing type, coordinates, and confidence
     */
    Gesture detect();

    /**
     * Gets the user-friendly name of this gesture detector.
     *
     * @return the name of the detector
     */
    String getName();

    /**
     * Calibrates the gesture detector tracker if supported.
     */
    default void calibrate() {
    }
}
