package com.starkmouse.detection;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import java.util.ArrayList;
import java.util.List;

public class ColorBlobDetector implements GestureDetector {

    private static final Scalar LOWER_BOUND = new Scalar(40, 80, 80);
    private static final Scalar UPPER_BOUND = new Scalar(80, 255, 255);
    private static final double MIN_CONTOUR_AREA = 400.0;
    private final Mat hsv = new Mat();
    private final Mat mask = new Mat();

    @Override
    public Gesture detect(Mat frame) {
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
        Core.inRange(hsv, LOWER_BOUND, UPPER_BOUND, mask);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        MatOfPoint largestContour = null;
        double largestArea = MIN_CONTOUR_AREA;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > largestArea) {
                largestArea = area;
                largestContour = contour;
            }
        }

        if (largestContour == null) {
            return Gesture.none();
        }

        Moments m = Imgproc.moments(largestContour);

        if (m.m00 == 0) {
            return Gesture.none();
        }

        int x = (int) (m.m10 / m.m00);
        int y = (int) (m.m01 / m.m00);

        double confidence = Math.min(1.0, largestArea / 5000.0);

        return new Gesture(Gesture.Type.POINT, x, y, confidence);
    }

    @Override
    public String getName() {
        return "Color Blob";
    }

    public Mat getLastMask() {
        return mask;
    }
}
