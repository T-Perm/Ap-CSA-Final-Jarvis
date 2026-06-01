package com.starkmouse.detection;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

public class HandContourDetector implements GestureDetector {

    private static final Scalar SKIN_LOWER = new Scalar(0, 133, 77);
    private static final Scalar SKIN_UPPER = new Scalar(255, 173, 127);
    private static final int MIN_HAND_AREA = 5000;
    private static final double MAX_FINGER_ANGLE_DEG = 90.0;
    private static final int MIN_DEFECT_DEPTH = 20;
    private static final int HOLD_MS_PEN = 1000;
    private static final int HOLD_MS_TOGGLE = 2000;

    private Mat ycrcb = new Mat();
    private Mat mask = new Mat();
    private Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));

    private int lastFingers = -1;
    private long heldSince = 0;
    private boolean penFired = false;
    private boolean toggleFired = false;

    @Override
    public Gesture detect(Mat frame) {
        Imgproc.cvtColor(frame, ycrcb, Imgproc.COLOR_BGR2YCrCb);
        Core.inRange(ycrcb, SKIN_LOWER, SKIN_UPPER, mask);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

        MatOfPoint largestContour = findLargestContour(mask);
        if (largestContour == null || Imgproc.contourArea(largestContour) < MIN_HAND_AREA) {
            return resetAndNone();
        }

        Moments m = Imgproc.moments(largestContour);
        double cx = m.m10 / m.m00;
        double cy = m.m01 / m.m00;
        double area = Imgproc.contourArea(largestContour);

        int fingers = countFingers(largestContour);
        Gesture.Type type = applyHoldLogic(fingers);

        double confidence = Math.min(1.0, area / 20000.0);
        return new Gesture(type, (int) cx, (int) cy, confidence);
    }

    private MatOfPoint findLargestContour(Mat mask) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint largest = null;
        double maxArea = 0;
        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            if (area > maxArea) {
                maxArea = area;
                largest = c;
            }
        }
        return largest;
    }

    private Gesture resetAndNone() {
        lastFingers = -1;
        penFired = false;
        toggleFired = false;
        return Gesture.none();
    }

    private int countFingers(MatOfPoint hand) {
        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(hand, hull, false);
        if (hull.rows() < 3) return 0;

        MatOfInt4 defects = new MatOfInt4();
        try {
            Imgproc.convexityDefects(hand, hull, defects);
        } catch (Exception e) {
            return 0;
        }

        int gaps = 0;
        Point[] pts = hand.toArray();
        int[] arr = defects.toArray();

        for (int i = 0; i < arr.length; i += 4) {
            double depth = arr[i + 3] / 256.0;
            if (depth < MIN_DEFECT_DEPTH) continue;

            Point a = pts[arr[i]];
            Point b = pts[arr[i + 1]];
            Point far = pts[arr[i + 2]];

            double angle = angleAtPoint(a, b, far);
            if (angle < MAX_FINGER_ANGLE_DEG) {
                gaps++;
            }
        }

        return Math.min(5, gaps + 1);
    }

    private double angleAtPoint(Point a, Point b, Point far) {
        double ab2 = sqDist(far, a);
        double cb2 = sqDist(far, b);
        double ac2 = sqDist(a, b);
        double cos = (ab2 + cb2 - ac2) / (2 * Math.sqrt(ab2) * Math.sqrt(cb2));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    private double sqDist(Point p, Point q) {
        return (p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y);
    }

    private Gesture.Type applyHoldLogic(int fingers) {
        boolean isFist = (fingers == 0);
        boolean isPalm = (fingers >= 4);
        long now = System.currentTimeMillis();

        if (fingers != lastFingers) {
            lastFingers = fingers;
            heldSince = now;
            penFired = false;
            toggleFired = false;
            return getBaseGesture(fingers);
        }

        long held = now - heldSince;

        if (isFist && held >= HOLD_MS_PEN && !penFired) {
            penFired = true;
            return Gesture.Type.PEN_DOWN;
        }

        if (isPalm) {
            if (held >= HOLD_MS_TOGGLE && !toggleFired) {
                toggleFired = true;
                return Gesture.Type.SCRATCHPAD_TOGGLE;
            } else if (held >= HOLD_MS_PEN && !penFired) {
                penFired = true;
                return Gesture.Type.PEN_UP;
            }
        }

        return getBaseGesture(fingers);
    }

    private Gesture.Type getBaseGesture(int fingers) {
        switch (fingers) {
            case 1: return Gesture.Type.POINT;
            case 2: return Gesture.Type.LEFT_CLICK;
            case 3: return Gesture.Type.RIGHT_CLICK;
            default: return Gesture.Type.NONE;
        }
    }

    @Override
    public String getName() {
        return "Hand Contour";
    }

    public Mat getLastMask() {
        return mask;
    }
}
