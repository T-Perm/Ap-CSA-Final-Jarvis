package com.starkmouse.app;

import com.starkmouse.detection.NativeGestureDetector;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Main application class for StarkMouse. Initializes the Swing HUD, global hotkeys,
 * and JNI tracker. Translates normalized hand landmark tracking coordinates to 
 * system robot mouse movements and clicks.
 */
public class MainApp {

    /**
     * Distance threshold to trigger a click pinch gesture (normalized coordinates).
     */
    private static final double PINCH_THRESHOLD = 0.06;          

    /**
     * Distance threshold to release a click pinch gesture (normalized coordinates).
     */
    private static final double PINCH_RELEASE_THRESHOLD = 0.09;  

    /**
     * Milliseconds delay before releasing a click to filter out coordinate drops.
     */
    private static final long DEBOUNCE_MS = 100;                 

    /**
     * Minimum cooldown duration in milliseconds between consecutive clicks.
     */
    private static final long COOLDOWN_MS = 200;                 

    /**
     * Exponential smoothing factor for cursor movement (0.0 to 1.0).
     */
    private static final double SMOOTHING = 0.35;                

    /**
     * Smoothed X coordinate of the mouse cursor.
     */
    private static double smoothedX = 0;

    /**
     * Smoothed Y coordinate of the mouse cursor.
     */
    private static double smoothedY = 0;

    /**
     * Flag indicating if the current coordinate is the first point of the tracker session.
     */
    private static boolean isFirstPoint = true;

    /**
     * Flag indicating if the left mouse button is currently pressed.
     */
    private static boolean leftPressed = false;

    /**
     * System timestamp of the last left-click pinch event.
     */
    private static long lastLeftPinchTime = 0;

    /**
     * System timestamp of the last left-click release event.
     */
    private static long lastLeftReleaseTime = 0;

    /**
     * Flag indicating if the right mouse button is currently pressed.
     */
    private static boolean rightPressed = false;

    /**
     * System timestamp of the last right-click pinch event.
     */
    private static long lastRightPinchTime = 0;

    /**
     * System timestamp of the last right-click release event.
     */
    private static long lastRightReleaseTime = 0;

    /**
     * Application entry point. Configures the robot, sets up UI window components,
     * registers global native listeners, and starts the webcam JNI tracking loop.
     *
     * @param args command-line arguments
     * @throws Exception if system resources cannot be initialized
     */
    public static void main(String[] args) throws Exception {
        System.out.println("[Main] Initializing Java StarkMouse Controller...");

        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        JFrame hudFrame = new JFrame("StarkMouse HUD");
        hudFrame.setUndecorated(true);
        hudFrame.setAlwaysOnTop(true);
        hudFrame.setSize(480, 360);
        hudFrame.setLocationRelativeTo(null); 

        HudPanel hudPanel = new HudPanel();
        hudFrame.add(hudPanel);

        final Point[] dragStart = { null };
        hudFrame.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart[0] = e.getPoint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart[0] = null;
            }
        });
        hudFrame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] != null) {
                    Point curr = e.getLocationOnScreen();
                    hudFrame.setLocation(curr.x - dragStart[0].x, curr.y - dragStart[0].y);
                }
            }
        });

        SwingUtilities.invokeLater(() -> hudFrame.setVisible(true));

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    if ((e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0 &&
                        (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0 &&
                        e.getKeyCode() == NativeKeyEvent.VC_G) {
                        SwingUtilities.invokeLater(() -> hudFrame.setVisible(!hudFrame.isVisible()));
                    }
                }
            });
        } catch (Exception ex) {
            System.err.println("[Main] Error registering global hotkey: " + ex.getMessage());
        }

        NativeGestureDetector detector = new NativeGestureDetector((jpegBytes, landmarks) -> {
            BufferedImage bufferedImage = null;
            if (jpegBytes != null && jpegBytes.length > 0) {
                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(jpegBytes);
                    bufferedImage = ImageIO.read(bais);
                } catch (Exception e) {
                    System.err.println("[Main] Error decoding JPEG frame: " + e.getMessage());
                }
            }

            if (landmarks != null && landmarks.length == 63) {
                double x = landmarks[9 * 3];
                double y = landmarks[9 * 3 + 1];

                double targetX = x * screenSize.width;
                double targetY = y * screenSize.height;

                if (isFirstPoint) {
                    smoothedX = targetX;
                    smoothedY = targetY;
                    isFirstPoint = false;
                } else {
                    smoothedX = smoothedX * (1.0 - SMOOTHING) + targetX * SMOOTHING;
                    smoothedY = smoothedY * (1.0 - SMOOTHING) + targetY * SMOOTHING;
                }

                robot.mouseMove((int) smoothedX, (int) smoothedY);

                double leftDist = Math.sqrt(Math.pow(landmarks[4 * 3] - landmarks[8 * 3], 2) + 
                                            Math.pow(landmarks[4 * 3 + 1] - landmarks[8 * 3 + 1], 2));
                double rightDist = Math.sqrt(Math.pow(landmarks[4 * 3] - landmarks[12 * 3], 2) + 
                                             Math.pow(landmarks[4 * 3 + 1] - landmarks[12 * 3 + 1], 2));

                long now = System.currentTimeMillis();

                if (leftDist < PINCH_THRESHOLD) {
                    lastLeftPinchTime = now;
                    if (!leftPressed && (now - lastLeftReleaseTime >= COOLDOWN_MS)) {
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        leftPressed = true;
                    }
                } else if (leftDist > PINCH_RELEASE_THRESHOLD) {
                    if (leftPressed && (now - lastLeftPinchTime >= DEBOUNCE_MS)) {
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                        leftPressed = false;
                        lastLeftReleaseTime = now;
                    }
                }

                if (rightDist < PINCH_THRESHOLD) {
                    lastRightPinchTime = now;
                    if (!rightPressed && (now - lastRightReleaseTime >= COOLDOWN_MS)) {
                        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                        rightPressed = true;
                    }
                } else if (rightDist > PINCH_RELEASE_THRESHOLD) {
                    if (rightPressed && (now - lastRightPinchTime >= DEBOUNCE_MS)) {
                        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                        rightPressed = false;
                        lastRightReleaseTime = now;
                    }
                }
            } else {
                isFirstPoint = true;
            }

            BufferedImage finalImg = bufferedImage;
            SwingUtilities.invokeLater(() -> {
                hudPanel.updateFrame(finalImg, landmarks, leftPressed, rightPressed);
            });
        });

        System.out.println("[Main] Starting native tracker...");
        detector.startTracker();
        System.out.println("[Main] StarkMouse Controller running. Press Ctrl+Shift+G to toggle HUD window.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Main] Stopping native tracker...");
            detector.stopTracker();
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (Exception e) {}
        }));

        while (true) {
            Thread.sleep(1000);
        }
    }

    /**
     * Swing Panel responsible for drawing the webcam frame overlayed with
     * MediaPipe hand skeleton connections and telemetry.
     */
    static class HudPanel extends JPanel {
        /**
         * The decoded webcam background image to render.
         */
        private BufferedImage currentImage = null;

        /**
         * Array of 63 double coordinates representing the 21 hand landmarks.
         */
        private double[] currentLandmarks = null;

        /**
         * State flag for drawing the left click pinch indicator.
         */
        private boolean isLeftPinching = false;

        /**
         * State flag for drawing the right click pinch indicator.
         */
        private boolean isRightPinching = false;

        /**
         * Index mapping tuples of landmark connections to draw the hand skeleton connections.
         */
        private static final int[][] CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4}, 
            {0, 5}, {5, 6}, {6, 7}, {7, 8}, 
            {0, 9}, {9, 10}, {10, 11}, {11, 12}, 
            {0, 13}, {13, 14}, {14, 15}, {15, 16}, 
            {0, 17}, {17, 18}, {18, 19}, {19, 20}, 
            {5, 9}, {9, 13}, {13, 17} 
        };

        /**
         * Updates the HUD display elements with the latest tracked details and triggers repaint.
         *
         * @param img           the updated camera frame image
         * @param landmarks     the tracking coordinate landmarks
         * @param leftPinching  true if left pinch gesture is active
         * @param rightPinching true if right pinch gesture is active
         */
        public void updateFrame(BufferedImage img, double[] landmarks, boolean leftPinching, boolean rightPinching) {
            this.currentImage = img;
            this.currentLandmarks = landmarks;
            this.isLeftPinching = leftPinching;
            this.isRightPinching = rightPinching;
            repaint();
        }

        /**
         * Paints the HUD panel overlay including webcam image, scan lines, landmarks, and click glows.
         *
         * @param g the Graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            if (currentImage != null) {
                g2d.drawImage(currentImage, 0, 0, width, height, null);
            } else {
                g2d.setColor(new Color(20, 20, 20));
                g2d.fillRect(0, 0, width, height);
                g2d.setColor(Color.LIGHT_GRAY);
                g2d.drawString("Waiting for camera feed...", width / 2 - 70, height / 2);
            }

            drawScanGrid(g2d, width, height);
            drawCornerBrackets(g2d, width, height);

            g2d.setColor(new Color(0, 255, 255, 180));
            g2d.setFont(new Font("Monospaced", Font.BOLD, 11));
            if (currentLandmarks != null && currentLandmarks.length == 63) {
                double leftDist = Math.sqrt(Math.pow(currentLandmarks[4 * 3] - currentLandmarks[8 * 3], 2) + 
                                            Math.pow(currentLandmarks[4 * 3 + 1] - currentLandmarks[8 * 3 + 1], 2));
                double rightDist = Math.sqrt(Math.pow(currentLandmarks[4 * 3] - currentLandmarks[12 * 3], 2) + 
                                             Math.pow(currentLandmarks[4 * 3 + 1] - currentLandmarks[12 * 3 + 1], 2));
                g2d.drawString(String.format("L-Pinch (Index): %.3f", leftDist), 15, 25);
                g2d.drawString(String.format("R-Pinch (Mid):   %.3f", rightDist), 15, 38);
                g2d.drawString(String.format("Status:          L-Click=%b R-Click=%b", isLeftPinching, isRightPinching), 15, 51);
            }

            if (currentLandmarks != null && currentLandmarks.length == 63) {
                g2d.setStroke(new BasicStroke(2));
                g2d.setColor(new Color(0, 255, 255, 150)); 
                for (int[] conn : CONNECTIONS) {
                    int p1 = conn[0];
                    int p2 = conn[1];
                    int x1 = (int) (currentLandmarks[p1 * 3] * width);
                    int y1 = (int) (currentLandmarks[p1 * 3 + 1] * height);
                    int x2 = (int) (currentLandmarks[p2 * 3] * width);
                    int y2 = (int) (currentLandmarks[p2 * 3 + 1] * height);
                    g2d.drawLine(x1, y1, x2, y2);
                }

                for (int i = 0; i < 21; i++) {
                    int x = (int) (currentLandmarks[i * 3] * width);
                    int y = (int) (currentLandmarks[i * 3 + 1] * height);

                    if (i == 4 || i == 8 || i == 12 || i == 16 || i == 20) {
                        g2d.setColor(Color.GREEN); 
                        g2d.fillOval(x - 5, y - 5, 10, 10);
                    } else {
                        g2d.setColor(new Color(0, 128, 255)); 
                        g2d.fillOval(x - 3, y - 3, 6, 6);
                    }
                    g2d.setColor(Color.WHITE);
                    g2d.drawOval(x - 3, y - 3, 6, 6);
                }

                if (isLeftPinching) {
                    drawPinchGlow(g2d, 4, 8, width, height, "LEFT CLICK");
                }
                if (isRightPinching) {
                    drawPinchGlow(g2d, 4, 12, width, height, "RIGHT CLICK");
                }
            }
        }

        /**
         * Draws faint gridlines over the HUD panel to give it a tech appearance.
         *
         * @param g2d the Graphics2D context
         * @param w   the width of the panel
         * @param h   the height of the panel
         */
        private void drawScanGrid(Graphics2D g2d, int w, int h) {
            g2d.setColor(new Color(0, 255, 255, 10)); 
            int step = 30;
            for (int x = 0; x < w; x += step) {
                g2d.drawLine(x, 0, x, h);
            }
            for (int y = 0; y < h; y += step) {
                g2d.drawLine(0, y, w, y);
            }
        }

        /**
         * Draws high-tech corner overlay brackets around the webcam HUD.
         *
         * @param g2d the Graphics2D context
         * @param w   the width of the panel
         * @param h   the height of the panel
         */
        private void drawCornerBrackets(Graphics2D g2d, int w, int h) {
            g2d.setColor(new Color(0, 255, 255, 80)); 
            g2d.setStroke(new BasicStroke(2));
            int len = 15;
            int gap = 5;

            g2d.drawLine(gap, gap, gap + len, gap);
            g2d.drawLine(gap, gap, gap, gap + len);

            g2d.drawLine(w - gap, gap, w - gap - len, gap);
            g2d.drawLine(w - gap, gap, w - gap, gap + len);

            g2d.drawLine(gap, h - gap, gap + len, h - gap);
            g2d.drawLine(gap, h - gap, gap, h - gap - len);

            g2d.drawLine(w - gap, h - gap, w - gap - len, h - gap);
            g2d.drawLine(w - gap, h - gap, w - gap, h - gap - len);
        }

        /**
         * Draws an animated glow and text label centered on the pinch location.
         *
         * @param g2d   the Graphics2D context
         * @param p1    landmark index 1
         * @param p2    landmark index 2
         * @param w     width of the panel
         * @param h     height of the panel
         * @param label descriptive label string
         */
        private void drawPinchGlow(Graphics2D g2d, int p1, int p2, int w, int h, String label) {
            int x1 = (int) (currentLandmarks[p1 * 3] * w);
            int y1 = (int) (currentLandmarks[p1 * 3 + 1] * h);
            int x2 = (int) (currentLandmarks[p2 * 3] * w);
            int y2 = (int) (currentLandmarks[p2 * 3 + 1] * h);

            int cx = (x1 + x2) / 2;
            int cy = (y1 + y2) / 2;

            g2d.setColor(new Color(255, 128, 0, 70)); 
            g2d.fillOval(cx - 20, cy - 20, 40, 40);
            g2d.setColor(new Color(255, 128, 0, 180));
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{3}, 0));
            g2d.drawOval(cx - 20, cy - 20, 40, 40);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            g2d.drawString(label, cx - 30, cy - 25);
        }
    }
}