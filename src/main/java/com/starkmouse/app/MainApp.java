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

        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        boolean leftClicked = false;
        boolean rightClicked = false;

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

            if (g.getType() == Gesture.Type.LEFT_CLICK) {
                if (!leftClicked) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    leftClicked = true;
                }
            } else {
                if (leftClicked) {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    leftClicked = false;
                }
            }

            if (g.getType() == Gesture.Type.RIGHT_CLICK) {
                if (!rightClicked) {
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                    rightClicked = true;
                }
            } else {
                if (rightClicked) {
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                    rightClicked = false;
                }
            }

            Thread.sleep(15);
        }
    }
}