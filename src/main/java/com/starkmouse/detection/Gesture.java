package com.starkmouse.detection;

/**
 * Representation of a detected hand gesture, holding its type, 
 * coordinates, and detection confidence.
 */
public class Gesture {
    /**
     * Types of gestures that can be recognized by the detector.
     */
    public enum Type {
        NONE,
        POINT,
        LEFT_CLICK,
        RIGHT_CLICK,
        PEN_DOWN,
        PEN_UP,
        SCRATCHPAD_TOGGLE
    }

    /**
     * The classified type of gesture.
     */
    private final Type type;

    /**
     * Normalized X coordinate of the cursor pointer (0.0 to 1.0).
     */
    private final double x;

    /**
     * Normalized Y coordinate of the cursor pointer (0.0 to 1.0).
     */
    private final double y;

    /**
     * The model confidence score for this detection (0.0 to 1.0).
     */
    private final double confidence;

    /**
     * Constructs a Gesture with the specified type, coordinate positions, and confidence.
     *
     * @param type       the type of gesture classified
     * @param x          the normalized X coordinate of the gesture
     * @param y          the normalized Y coordinate of the gesture
     * @param confidence the tracking model confidence level
     */
    public Gesture(Type type, double x, double y, double confidence) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }

    /**
     * Gets the type of the gesture.
     *
     * @return the gesture type enum value
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the normalized X coordinate.
     *
     * @return the normalized X coordinate value
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the normalized Y coordinate.
     *
     * @return the normalized Y coordinate value
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the confidence level.
     *
     * @return the confidence level value
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Creates an empty/default gesture representing no action.
     *
     * @return a default Gesture object representing Type.NONE
     */
    public static Gesture none() {
        return new Gesture(Type.NONE, 0.0, 0.0, 0.0);
    }

    /**
     * Returns a string representation of the gesture details.
     *
     * @return formatted string containing type, coordinates, and confidence
     */
    @Override
    public String toString() {
        return String.format("%s @ (%.3f,%.3f) c=%.2f", type, x, y, confidence);
    }
}
