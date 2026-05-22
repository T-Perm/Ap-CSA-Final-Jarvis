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
    private final int x;
    private final int y;
    private final double confidence;

    public Gesture(Type type, int x, int y, double confidence) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }

    public Type getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public double getConfidence() {
        return confidence;
    }

    public static Gesture none() {
        return new Gesture(Type.NONE, 0, 0, 0.0);
    }

    @Override
    public String toString() {
        return String.format("%s @ (%d,%d) c=%.2f", type, x, y, confidence);
    }
}
