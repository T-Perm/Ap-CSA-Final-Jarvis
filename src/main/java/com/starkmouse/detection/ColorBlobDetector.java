package com.starkmouse.detection;

import org.opencv.core.Mat;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of GestureDetector that tracks a single, brightly-colored
 * blob (marker)
 * within a specified HSV range and reports its centroid as a POINT gesture.
 */
public class ColorBlobDetector implements GestureDetector {

    /** Lower bound for the HSV color thresholding (e.g., orange). */
    private static final Scalar LOWER_BOUND = new Scalar(40, 80, 80);

    /** Upper bound for the HSV color thresholding (e.g., orange). */
    private static final Scalar UPPER_BOUND = new Scalar(80, 255, 255);

    /** Minimum contour area in pixels to filter out noise. */
    private static final double MIN_CONTOUR_AREA = 400.0;

    /** Reusable buffer for the HSV-converted frame. */
    private final Mat hsv = new Mat();

    /** Reusable buffer for the binary mask. */
    private final Mat mask = new Mat();

    /**
     * Detects a colored blob in the given frame and computes its centroid
     * coordinates.
     * 
     * @param frame the input camera frame in BGR format
     * @return a POINT Gesture pointing at the centroid of the largest color blob,
     *         or Gesture.none() if no valid blob is found
     */
    @Override
    public Gesture detect(Mat frame) {
        // Convert input BGR frame to HSV color space
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

        // Threshold the HSV frame to produce a binary mask of in-range pixels
        Core.inRange(hsv, LOWER_BOUND, UPPER_BOUND, mask);

        // Find contours (blobs) in the mask
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchy.release();

        MatOfPoint largestContour = null;
        double largestArea = MIN_CONTOUR_AREA;

        // Iterate through all contours to find the largest one above the threshold
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > largestArea) {
                largestArea = area;
                largestContour = contour;
            }
        }

        // Return NONE gesture if no contour meets the criteria
        if (largestContour == null) {
            return Gesture.none();
        }

        // Compute centroid (center of mass) of the largest contour using image moments
        Moments m = Imgproc.moments(largestContour);

        // Prevent division by zero if area/mass (m00) is somehow 0
        if (m.m00 == 0) {
            return Gesture.none();
        }

        int x = (int) (m.m10 / m.m00);
        int y = (int) (m.m01 / m.m00);

        // Calculate a scaled confidence based on contour area, capped at 1.0
        double confidence = Math.min(1.0, largestArea / 5000.0);

        return new Gesture(Gesture.Type.POINT, x, y, confidence);
    }

    /**
     * Gets the user-friendly display name of this detector.
     * 
     * @return the string "Color Blob"
     */
    @Override
    public String getName() {
        return "Color Blob";
    }

    public Mat getLastMask() {
        return mask;
    }
}
