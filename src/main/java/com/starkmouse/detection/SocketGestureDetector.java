package com.starkmouse.detection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;


public class SocketGestureDetector implements GestureDetector {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private volatile Gesture latestGesture = Gesture.none();
    private boolean running = true;

    public SocketGestureDetector(String host, int port) {
        this.host = host;
        this.port = port;
        startReceiver();
    }

    private void startReceiver() {
        Thread thread = new Thread(() -> {
            while (running) {
                try {
                    System.out.println("[Socket] Connecting to " + host + ":" + port + "...");
                    socket = new Socket(host, port);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    System.out.println("[Socket] Connected to tracker.");

                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        try {
                            String[] parts = line.split(",");
                            if (parts.length >= 4) {
                                Gesture.Type type = Gesture.Type.valueOf(parts[0]);
                                double x = Double.parseDouble(parts[1]);
                                double y = Double.parseDouble(parts[2]);
                                double conf = Double.parseDouble(parts[3]);
                                latestGesture = new Gesture(type, x, y, conf);
                            }
                        } catch (Exception e) {
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Socket] Connection error: " + e.getMessage() + ". Retrying in 2s...");
                    latestGesture = Gesture.none();
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                } finally {
                    closeResources();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void closeResources() {
        try {
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
        }
    }

    public void close() {
        running = false;
        closeResources();
    }

    @Override
    public Gesture detect() {
        return latestGesture;
    }

    @Override
    public String getName() {
        return "Socket Detector";
    }
}
