package Ap-CSA-Final-Jarvis;

public interface GestureDetector {
    Gesture detect(Mat frame);
    String getName();
    default void calibrate(){}
}
