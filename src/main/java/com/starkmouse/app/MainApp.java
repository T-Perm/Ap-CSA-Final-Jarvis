package com.starkmouse.app;

import com.starkmouse.detection.HandContourDetector;
import com.starkmouse.detection.Gesture;
import com.starkmouse.input.CameraInput;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public class MainApp {

    static BufferedImage toImage(Mat mat) throws Exception {
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buf);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(buf.toArray()));
        buf.release();
        return img;
    }

    public static void main(String[] args) throws Exception {
        OpenCV.loadLocally();

        CameraInput cam = new CameraInput(0);
        cam.start();

        HandContourDetector det = new HandContourDetector();

        // feed window - left side of screen
        JLabel feedLabel = new JLabel();
        JFrame feedFrame = new JFrame("ch7 - Hand Contour test");
        feedFrame.add(feedLabel);
        feedFrame.setSize(680, 540);
        feedFrame.setLocation(0, 0);
        feedFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        feedFrame.setVisible(true);

        // mask window - right side of screen
        JLabel maskLabel = new JLabel();
        JFrame maskFrame = new JFrame("ch7 - mask (tune HSV here)");
        maskFrame.add(maskLabel);
        maskFrame.setSize(680, 540);
        maskFrame.setLocation(700, 0);
        maskFrame.setVisible(true);

        Mat annotated = new Mat();

        while (feedFrame.isVisible()) {
            Mat frame = cam.grabFrame();
            if (frame == null) {
                Thread.sleep(30);
                continue;
            }

            Gesture g = det.detect(frame);

            // copy the frame and draw the tracked point on it
            frame.copyTo(annotated);
            if (g.getType() != Gesture.Type.NONE) {
                // orange circle at the centroid
                Imgproc.circle(annotated,
                        new Point(g.getX(), g.getY()),
                        16, new Scalar(0, 140, 255), 3);
                // crosshair
                Imgproc.line(annotated,
                        new Point(g.getX() - 24, g.getY()),
                        new Point(g.getX() + 24, g.getY()),
                        new Scalar(0, 140, 255), 1);
                Imgproc.line(annotated,
                        new Point(g.getX(), g.getY() - 24),
                        new Point(g.getX(), g.getY() + 24),
                        new Scalar(0, 140, 255), 1);
                // print gesture info on the image
                Imgproc.putText(annotated,
                        g.toString(),
                        new Point(10, 30),
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.7,
                        new Scalar(0, 255, 0), 2);
            }

            feedLabel.setIcon(new ImageIcon(toImage(annotated)));
            feedFrame.repaint();

            // expose the mask so you can tune HSV bounds
            // NOTE: this requires you to make `mask` accessible in
            // your ColorBlobDetector, e.g. package-private or via a
            // getMask() method. Alternatively: duplicate the threshold
            // logic here temporarily just for the debug view.
            // Easiest quick hack: add `Mat getLastMask() { return mask; }`
            // to your ColorBlobDetector, delete it when done.
            Mat mask = det.getLastMask(); // add this method temporarily
            if (mask != null && !mask.empty()) {
                maskLabel.setIcon(new ImageIcon(toImage(mask)));
                maskFrame.repaint();
            }

            Thread.sleep(30);
        }

        cam.stop();
    }
}