package com.starkmouse.detection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Socket-based gesture detector that connects to an external Python socket server.
 * Used as a fallback or mock implementation.
 */
public class SocketGestureDetector implements GestureDetector {
    /**
     * Host address of the socket server.
     */
    private final String host;

    /**
     * Port number of the socket server.
     */
    private final int port;

    /**
     * TCP Socket object connected to the server.
     */
    private Socket socket;

    /**
     * Reader to read stream lines from socket input.
     */
    private BufferedReader reader;

    /**
     * Cache for the most recently received gesture.
     */
    private volatile Gesture latestGesture = Gesture.none();

    /**
     * Flag indicating if the background receiver thread should keep running.
     */
    private boolean running = true;

    /**
     * Constructs a SocketGestureDetector and initiates connection to host and port.
     *
     * @param host the remote server hostname
     * @param port the remote server socket port
     */
    public SocketGestureDetector(String host, int port) {
        this.host = host;
        this.port = port;
        startReceiver();
    }

    /**
     * Starts the daemon thread to establish socket connection and receive gesture logs.
     */
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

    /**
     * Safely closes open TCP streams and socket connections.
     */
    private synchronized void closeResources() {
        try {
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
        }
    }

    /**
     * Terminates the background receiver thread and closes socket resources.
     */
    public void close() {
        running = false;
        closeResources();
    }

    /**
     * Detects and returns the latest cached gesture.
     *
     * @return the latest Gesture received
     */
    @Override
    public Gesture detect() {
        return latestGesture;
    }

    /**
     * Gets the name of the detector type.
     *
     * @return the name representation of this socket detector
     */
    @Override
    public String getName() {
        return "Socket Detector";
    }
}
