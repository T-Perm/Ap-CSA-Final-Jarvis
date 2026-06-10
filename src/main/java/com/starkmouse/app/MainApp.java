package com.starkmouse.app;

import com.starkmouse.detection.NativeGestureDetector;
import com.starkmouse.detection.Gesture;

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;

public class MainApp {
    
    public static void main(String[] args) throws Exception {
        System.out.println("[Main] Starting native tracker...");
        
        // Configuration parameters for mouse clicks (feel free to adjust these!)
        long debounceMs = 150;  // Time (ms) the gesture must be absent before releasing the click (filters camera noise/dropouts)
        long cooldownMs = 200;  // Minimum time (ms) required between consecutive clicks (prevents accidental double clicks)
        
        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        // State variables for Left Mouse Button (primary click, controlled by RIGHT_CLICK gesture)
        boolean leftMousePressed = false;
        long lastLeftGestureTime = 0;
        long lastLeftReleaseTime = 0;
        
        // State variables for Right Mouse Button (secondary click, controlled by LEFT_CLICK gesture)
        boolean rightMousePressed = false;
        long lastRightGestureTime = 0;
        long lastRightReleaseTime = 0;

        final Gesture[] latest = { Gesture.none() };
        final Object lock = new Object();

        NativeGestureDetector detector = new NativeGestureDetector(g -> {
            synchronized (lock) {
                latest[0] = g;
            }
        });

        detector.startTracker();
        System.out.println("[Main] Native tracker started.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Shutting down tracker...");
        }));

        while (true) {
            Gesture g;
            synchronized (lock) {
                g = latest[0];
            }

            if (g.getType() != Gesture.Type.NONE) {
                int screenX = (int) (g.getX() * screenSize.width);
                int screenY = (int) (g.getY() * screenSize.height);
                robot.mouseMove(screenX, screenY);
            }
            
            long now = System.currentTimeMillis();

            // Handle Left Mouse Button (primary click, controlled by RIGHT_CLICK gesture)
            if (g.getType() == Gesture.Type.RIGHT_CLICK) {
                lastLeftGestureTime = now;
                if (!leftMousePressed && (now - lastLeftReleaseTime >= cooldownMs)) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    leftMousePressed = true;
                }
            } else {
                if (leftMousePressed && (now - lastLeftGestureTime >= debounceMs)) {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    leftMousePressed = false;
                    lastLeftReleaseTime = now;
                }
            }

            // Handle Right Mouse Button (secondary click, controlled by LEFT_CLICK gesture)
            if (g.getType() == Gesture.Type.LEFT_CLICK) {
                lastRightGestureTime = now;
                if (!rightMousePressed && (now - lastRightReleaseTime >= cooldownMs)) {
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                    rightMousePressed = true;
                }
            } else {
                if (rightMousePressed && (now - lastRightGestureTime >= debounceMs)) {
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                    rightMousePressed = false;
                    lastRightReleaseTime = now;
                }
            }

            Thread.sleep(15);
        }
    }
}