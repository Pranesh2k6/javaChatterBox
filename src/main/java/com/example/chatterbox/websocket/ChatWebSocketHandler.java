package com.example.chatterbox.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket ↔ TCP bridge.
 *
 * Each browser WebSocket connection gets its own TCP socket to the
 * ChatServer on port 9000. This handler:
 *
 *  1. Authenticates the browser via the HTTP session (populated by
 *     HttpSessionHandshakeInterceptor on handshake).
 *  2. Opens a TCP socket to localhost:9000 and sends the username
 *     as the first line (ChatServer registration protocol).
 *  3. Starts a background reader thread that forwards every TCP line
 *     back to the browser WebSocket client.
 *  4. Forwards every WebSocket text frame directly to the TCP server.
 *  5. Closes the TCP socket cleanly when the WebSocket disconnects.
 *
 * No JSON is used over TCP — all TCP messages are plain text strings
 * following the protocol defined in ClientHandler / ChatServer.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String TCP_HOST = "localhost";
    private static final int TCP_PORT = 9000;

    /**
     * Map from WebSocket session ID → TcpConnection.
     * ConcurrentHashMap for thread-safe access across reader threads
     * and the WebSocket event callbacks.
     */
    private final ConcurrentHashMap<String, TcpConnection> connections =
            new ConcurrentHashMap<>();

    // ----------------------------------------------------------------
    // CONNECTION ESTABLISHED
    // ----------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {

        // 1. Retrieve session attributes injected by the handshake interceptor
        Long userId =
                (Long) session.getAttributes().get("userId");
        String username =
                (String) session.getAttributes().get("username");

        if (userId == null || username == null) {

            // Unauthenticated connection — reject it
            session.close(CloseStatus.POLICY_VIOLATION
                    .withReason("Not authenticated. Please log in first."));

            System.out.println(
                    "WebSocket rejected (unauthenticated): "
                    + session.getId()
            );

            return;
        }

        System.out.println("WebSocket connected: " + username
                + " (session=" + session.getId() + ")");

        // 2. Open a TCP connection to the chat server
        Socket tcpSocket;

        try {

            tcpSocket = new Socket(TCP_HOST, TCP_PORT);

        } catch (IOException e) {

            System.err.println(
                    "Cannot connect to TCP server for " + username
                    + ": " + e.getMessage()
            );

            session.close(CloseStatus.SERVICE_RESTARTED
                    .withReason("Chat server unavailable."));

            return;
        }

        BufferedReader tcpReader = new BufferedReader(
                new InputStreamReader(
                        tcpSocket.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );

        BufferedWriter tcpWriter = new BufferedWriter(
                new OutputStreamWriter(
                        tcpSocket.getOutputStream(),
                        StandardCharsets.UTF_8
                )
        );

        // 3. Send username as the first TCP line (registration)
        tcpWriter.write(username);
        tcpWriter.newLine();
        tcpWriter.flush();

        // 4. Read the initial registration response from the TCP server
        String firstResponse;

        try {

            firstResponse = tcpReader.readLine();

        } catch (IOException e) {

            firstResponse = null;
        }

        if (firstResponse == null ||
                firstResponse.startsWith("ERROR")) {

            // Registration rejected
            String reason = firstResponse != null
                    ? firstResponse
                    : "No response from chat server";

            sendSafe(session, reason);
            session.close(CloseStatus.POLICY_VIOLATION
                    .withReason(reason));

            try { tcpSocket.close(); } catch (IOException ignored) {}

            return;
        }

        // Send the success response back to the browser
        sendSafe(session, firstResponse);

        // 5. Build the connection record
        TcpConnection conn = new TcpConnection(
                tcpSocket, tcpReader, tcpWriter
        );

        connections.put(session.getId(), conn);

        // 6. Start background reader thread: TCP → WebSocket
        Thread readerThread = new Thread(() -> {

            try {

                String line;

                while ((line = tcpReader.readLine()) != null) {

                    sendSafe(session, line);
                }

            } catch (IOException e) {

                // TCP socket was closed — close the WebSocket too
                if (session.isOpen()) {

                    try {

                        session.close(CloseStatus.GOING_AWAY);

                    } catch (IOException ignored) {}
                }

            } finally {

                // Clean up regardless
                TcpConnection c = connections.remove(session.getId());

                if (c != null) {
                    c.close();
                }
            }

        }, "ws-tcp-reader-" + session.getId());

        conn.setReaderThread(readerThread);

        readerThread.setDaemon(true);
        readerThread.start();
    }

    // ----------------------------------------------------------------
    // MESSAGE FROM BROWSER → TCP SERVER
    // ----------------------------------------------------------------

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {

        TcpConnection conn = connections.get(session.getId());

        if (conn == null) {
            sendSafe(session, "ERROR: Not connected to chat server.");
            return;
        }

        String text = message.getPayload();

        try {

            synchronized (conn.writer) {

                conn.writer.write(text);
                conn.writer.newLine();
                conn.writer.flush();
            }

        } catch (IOException e) {

            sendSafe(session, "ERROR: Failed to send message: "
                    + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // CONNECTION CLOSED
    // ----------------------------------------------------------------

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {

        TcpConnection conn = connections.remove(session.getId());

        if (conn != null) {

            // Send /quit to the TCP server so it cleans up gracefully
            try {

                synchronized (conn.writer) {

                    conn.writer.write("/quit");
                    conn.writer.newLine();
                    conn.writer.flush();
                }

            } catch (IOException ignored) {
                // Best-effort; we close the socket next regardless
            }

            conn.close();
        }

        System.out.println("WebSocket closed: "
                + session.getId() + " (" + status + ")");
    }

    // ----------------------------------------------------------------
    // ERROR HANDLING
    // ----------------------------------------------------------------

    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable exception) {

        System.err.println(
                "WebSocket transport error for "
                + session.getId() + ": " + exception.getMessage()
        );

        TcpConnection conn = connections.remove(session.getId());

        if (conn != null) {
            conn.close();
        }
    }

    // ----------------------------------------------------------------
    // HELPER: thread-safe WebSocket send
    // ----------------------------------------------------------------

    /**
     * Send a text frame to the WebSocket client.
     * Synchronized on the session to prevent concurrent sends from
     * the reader thread and the WebSocket event thread.
     */
    private void sendSafe(WebSocketSession session, String text) {

        if (!session.isOpen()) {
            return;
        }

        try {

            synchronized (session) {

                session.sendMessage(new TextMessage(text));
            }

        } catch (IOException e) {

            System.err.println(
                    "Failed to send WebSocket message to "
                    + session.getId() + ": " + e.getMessage()
            );
        }
    }

    // ----------------------------------------------------------------
    // INNER CLASS: TcpConnection
    // ----------------------------------------------------------------

    /**
     * Holds all resources for a single TCP connection backing
     * a WebSocket session.
     */
    private static class TcpConnection {

        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter writer;

        private Thread readerThread;

        TcpConnection(Socket socket,
                      BufferedReader reader,
                      BufferedWriter writer) {

            this.socket = socket;
            this.reader = reader;
            this.writer = writer;
        }

        void setReaderThread(Thread readerThread) {
            this.readerThread = readerThread;
        }

        /** Close all resources associated with this TCP connection. */
        void close() {

            try {
                socket.close();
            } catch (IOException ignored) {}

            if (readerThread != null &&
                    readerThread.isAlive()) {

                readerThread.interrupt();
            }
        }
    }
}
