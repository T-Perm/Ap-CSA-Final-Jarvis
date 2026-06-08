package com.starkmouse.app;
import com.starkmouse.detection.SocketGestureDetector;
import com.starkmouse.detection.Gesture;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Socket;

public class MainApp {
    private static final int GESTURE_PORT = 5005;
    private static final int FRAME_PORT   = 5006;
    private static volatile BufferedImage latestFrame = null;

    public static void main(String[] args) throws Exception {

        System.out.println("[Main] Starting tracker...");
        Process pythonProcess = launchPython();
        SocketGestureDetector det = new SocketGestureDetector("127.0.0.1", GESTURE_PORT);
        startFrameReceiver();
        JLabel feedLabel = new JLabel("Waiting for feed...");
        JFrame feedFrame = new JFrame("Hand Tracker");
        feedFrame.add(feedLabel);
        feedFrame.setSize(680, 520);
        feedFrame.setLocation(50, 50);
        feedFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        feedFrame.setVisible(true);

        Robot robot = new Robot();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        boolean leftClicked = false;
        boolean rightClicked = false;
        boolean sized = false;

        while (feedFrame.isVisible()) {
            Gesture g = det.detect();
            if (g != null) {
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
            }

            BufferedImage img = latestFrame;
            if (img != null) {
                feedLabel.setIcon(new ImageIcon(img));
                feedLabel.setText(null);
                if (!sized) {
                    feedFrame.setSize(img.getWidth() + 16, img.getHeight() + 39);
                    sized = true;
                }
                feedFrame.repaint();
            }
            Thread.sleep(15);
        }
        det.close();
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            System.out.println("[Main] Python process terminated.");
        }
    }

    private static void startFrameReceiver() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    System.out.println("[FrameRx] Connecting to 127.0.0.1:" + FRAME_PORT + "...");
                    Socket sock = new Socket("127.0.0.1", FRAME_PORT);
                    DataInputStream dis = new DataInputStream(sock.getInputStream());
                    java.io.OutputStream os = sock.getOutputStream();
                    System.out.println("[FrameRx] Connected.");
                    int frameCount = 0;
                    while (true) {
                        os.write(1);
                        os.flush();
                        int len = dis.readInt();           
                        frameCount++;
                        if (len > 0) {
                            if (len > 10 * 1024 * 1024) {  
                                throw new java.io.IOException("Frame too large: " + len);
                            }
                            byte[] jpeg = new byte[len];
                            dis.readFully(jpeg);               
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                            if (img != null) {
                                latestFrame = img;
                            }
                        }
                        Thread.sleep(16);                  
                    }
                } catch (Exception e) {
                    System.err.println("[FrameRx] " + e.getMessage() + ". Retrying in 2s...");
                    latestFrame = null;
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static Process launchPython() {
        File script = new File("main.py");
        if (!script.exists()) {
            System.err.println("[Main] ERROR: main.py not found in "
                    + new File(".").getAbsolutePath());
            System.err.println("[Main] Start it manually:  python main.py");
            return null;
        }
        try {
            String py = findPython();
            System.out.println("[Main] Launching: " + py + " -u main.py");
            ProcessBuilder pb = new ProcessBuilder(py, "-u", "main.py");
            pb.directory(new File(".").getAbsoluteFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            Thread pipe = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[Python] " + line);
                    }
                } catch (Exception e) {  }
                try {
                    int exitCode = proc.waitFor();
                    System.err.println("[Main] Python process exited with code " + exitCode);
                } catch (Exception ignored) {}
            });
            pipe.setDaemon(true);
            pipe.start();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            if (!proc.isAlive()) {
                System.err.println("[Main] Python exited with code " + proc.exitValue());
                System.err.println("[Main] Run:  pip install mediapipe opencv-python");
                return null;
            }
            System.out.println("[Main] Python process running.");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (proc.isAlive()) {
                    proc.destroy();
                    System.out.println("[Main] Python cleaned up.");
                }
            }));
            return proc;
        } catch (Exception e) {
            System.err.println("[Main] Failed to launch Python: " + e.getMessage());
            System.err.println("[Main] Start it manually:  python main.py");
            return null;
        }
    }

    private static String findPython() {
        for (String cmd : new String[]{"py", "python", "python3"}) {
            try {
                Process p = new ProcessBuilder(cmd, "--version")
                        .redirectErrorStream(true).start();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                String ver = br.readLine();
                int exit = p.waitFor();
                if (exit == 0) {
                    System.out.println("[Main] Found: " + cmd + " → " + ver);
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        System.err.println("[Main] WARNING: python not found on PATH.");
        return "python";
    }
}