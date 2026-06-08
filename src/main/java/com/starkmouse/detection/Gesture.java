package com.starkmouse.detection;

public class Gesture {
    public enum Type {
        NONE,
        POINT,
        LEFT_CLICK,
        RIGHT_CLICK,
        PEN_DOWN,
        PEN_UP,
        SCRATCHPAD_TOGGLE
    }

    private final Type type;
    private final double x;
    private final double y;
    private final double confidence;

    public Gesture(Type type, double x, double y, double confidence) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }

    public Type getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getConfidence() {
        return confidence;
    }

    public static Gesture none() {
        return new Gesture(Type.NONE, 0.0, 0.0, 0.0);
    }

    @Override
    public String toString() {
        return String.format("%s @ (%.3f,%.3f) c=%.2f", type, x, y, confidence);
    }
}
