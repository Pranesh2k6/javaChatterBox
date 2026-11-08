package com.example.chatterbox.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handles a single TCP client connection.
 * Each browser WebSocket connection establishes its own TCP client.
 *
 * Protocol (client → server):
 *   First line:  username (registration)
 *   Subsequent:  /all, /msg, /room, /join, /leave, /list_rooms,
 *                /create_room, /kick, /typing_room, /typing_pm,
 *                /edit, /delete, /online, /history_room,
 *                /history_pm, /read_room, /read_pm, /quit
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;

    private String username;
    private boolean registered = false;

    private BufferedReader input;
    private BufferedWriter output;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {

        try {

            input = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            output = new BufferedWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            // First line received is the username for registration
            username = input.readLine();

            if (username == null ||
                    !username.matches("[A-Za-z0-9_]{3,20}")) {

                sendMessage(
                        "ERROR: Username must contain 3-20 letters, numbers or underscores."
                );

                return;
            }

            if (!server.registerClient(username, this)) {
                sendMessage("ERROR: Username already taken.");
                return;
            }

            registered = true;
            sendMessage("Connected successfully as " + username);

            System.out.println(username + " joined the chat.");

            server.broadcast(
                    "[SYSTEM] " + username + " joined the chat.",
                    this
            );
            server.broadcast(
                    server.getOnlineUsersList(),
                    this
            );

            String message;

            // Continuously listen for client commands
            while ((message = input.readLine()) != null) {

                if (message.equals("/quit")) {
                    break;
                }

                processMessage(message);
            }

        } catch (IOException e) {

            System.out.println(
                    "Connection error for " + username
                    + ": " + e.getMessage()
            );

        } finally {

            cleanup();
        }
    }

    // ----------------------------------------------------------------
    // COMMAND DISPATCH
    // ----------------------------------------------------------------

    private void processMessage(String message) {

        // ---- General broadcast (/all <content>) -------------------
        if (message.startsWith("/all ")) {

            String content = message.substring(5).trim();

            if (!content.isEmpty()) {

                try {

                    Long messageId =
                            server.saveGeneralMessage(username, content);

                    server.broadcast(
                            "[ALL:" + messageId + "] " + username + ": " + content,
                            this
                    );

                } catch (RuntimeException e) {

                    sendMessage("ERROR: " + e.getMessage());
                }
            }

        // ---- Private message (/msg <username> <content>) -----------
        } else if (message.startsWith("/msg ")) {

            String content = message.substring(5).trim();
            int sep = content.indexOf(' ');

            if (sep <= 0 || sep == content.length() - 1) {
                sendMessage("Usage: /msg <username> <message>");
                return;
            }

            String receiver = content.substring(0, sep);
            String pmContent = content.substring(sep + 1);

            try {

                Long msgId = server.savePrivateMessage(
                        username, receiver, pmContent
                );

                sendMessage("[PRIVATE_SENT:" + msgId + ":" + receiver + "]: "
                        + pmContent);

                boolean delivered = server.sendPrivateMessage(
                        receiver,
                        "[PRIVATE:" + msgId + "] " + username + ": " + pmContent
                );

                if (!delivered) {
                    sendMessage("[SYSTEM] " + receiver
                            + " is offline. Message saved.");
                }

            } catch (RuntimeException e) {

                sendMessage("ERROR: " + e.getMessage());
            }

        // ---- Room message (/room <roomName> <content>) -------------
        } else if (message.startsWith("/room ")) {

            String rest = message.substring(6).trim();
            int sep = rest.indexOf(' ');

            if (sep <= 0 || sep == rest.length() - 1) {
                sendMessage("Usage: /room <roomName> <message>");
                return;
            }

            String roomName = rest.substring(0, sep);
            String roomContent = rest.substring(sep + 1);

            try {

                Long msgId = server.saveRoomMessage(
                        username, roomName, roomContent
                );

                server.broadcastToRoom(
                        roomName,
                        "[ROOM:" + roomName + ":" + msgId + "] "
                        + username + ": " + roomContent,
                        this
                );

            } catch (RuntimeException e) {

                sendMessage("ERROR: " + e.getMessage());
            }

        // ---- Join room (/join <roomName>) --------------------------
        } else if (message.startsWith("/join ")) {

            String roomName = message.substring(6).trim();

            if (roomName.isEmpty()) {
                sendMessage("Usage: /join <roomName>");
                return;
            }

            String result = server.joinRoom(username, roomName);
            sendMessage(result);

        // ---- Leave room (/leave <roomName>) ------------------------
        } else if (message.startsWith("/leave ")) {

            String roomName = message.substring(7).trim();

            if (roomName.isEmpty()) {
                sendMessage("Usage: /leave <roomName>");
                return;
            }

            String result = server.leaveRoom(username, roomName);
            sendMessage(result);

        // ---- List rooms (/list_rooms) ------------------------------
        } else if (message.equals("/list_rooms")) {

            String roomList = server.listRooms();
            sendMessage(roomList);

        // ---- Create room (/create_room <name>) ---------------------
        } else if (message.startsWith("/create_room ")) {

            String roomName = message.substring(13).trim();

            if (roomName.isEmpty()) {
                sendMessage("Usage: /create_room <roomName>");
                return;
            }

            String result = server.createRoom(username, roomName);
            sendMessage(result);

        // ---- Kick member (/kick <roomName> <targetUser>) -----------
        } else if (message.startsWith("/kick ")) {

            String rest = message.substring(6).trim();
            int sep = rest.lastIndexOf(' ');

            if (sep <= 0 || sep == rest.length() - 1) {
                sendMessage("Usage: /kick <roomName> <username>");
                return;
            }

            String roomName = rest.substring(0, sep).trim();
            String target = rest.substring(sep + 1).trim();

            String result = server.kickMember(
                    username, roomName, target
            );

            sendMessage(result);

        // ---- Typing indicator – room (/typing_room <roomName>) ----
        } else if (message.startsWith("/typing_room ")) {

            String roomName = message.substring(13).trim();

            if (!roomName.isEmpty()) {
                server.broadcastTypingRoom(username, roomName);
            }

        // ---- Typing indicator – PM (/typing_pm <username>) --------
        } else if (message.startsWith("/typing_pm ")) {

            String targetUser = message.substring(11).trim();

            if (!targetUser.isEmpty()) {
                server.sendTypingPm(username, targetUser);
            }

        // ---- Edit message (/edit <messageId> <newContent>) ---------
        } else if (message.startsWith("/edit ")) {

            String rest = message.substring(6).trim();
            int sep = rest.indexOf(' ');

            if (sep <= 0 || sep == rest.length() - 1) {
                sendMessage("Usage: /edit <messageId> <newContent>");
                return;
            }

            try {

                Long messageId = Long.parseLong(
                        rest.substring(0, sep)
                );

                String newContent = rest.substring(sep + 1);

                String result = server.editMessage(
                        username, messageId, newContent
                );

                sendMessage(result);

            } catch (NumberFormatException e) {

                sendMessage("ERROR: Invalid message ID.");
            }

        // ---- Delete message (/delete <messageId>) ------------------
        } else if (message.startsWith("/delete ")) {

            String idStr = message.substring(8).trim();

            try {

                Long messageId = Long.parseLong(idStr);

                String result = server.deleteMessage(
                        username, messageId
                );

                sendMessage(result);

            } catch (NumberFormatException e) {

                sendMessage("ERROR: Invalid message ID.");
            }

        // ---- Online users (/online) --------------------------------
        } else if (message.equals("/online")) {

            sendMessage(server.getOnlineUsersList());

        // ---- Room history (/history_room <roomName> <limit>) ------
        } else if (message.startsWith("/history_room ")) {

            String rest = message.substring(14).trim();
            int sep = rest.lastIndexOf(' ');

            if (sep <= 0) {
                sendMessage("Usage: /history_room <roomName> <limit>");
                return;
            }

            String roomName = rest.substring(0, sep).trim();
            int limit = 20;

            try {
                limit = Integer.parseInt(
                        rest.substring(sep + 1).trim()
                );
            } catch (NumberFormatException ignored) {
                // use default
            }

            List<String> history =
                    server.getRoomHistory(username, roomName, limit);

            for (String line : history) {
                sendMessage(line);
            }

        // ---- PM history (/history_pm <username> <limit>) ----------
        } else if (message.startsWith("/history_pm ")) {

            String rest = message.substring(12).trim();
            int sep = rest.lastIndexOf(' ');

            if (sep <= 0) {
                sendMessage("Usage: /history_pm <username> <limit>");
                return;
            }

            String otherUser = rest.substring(0, sep).trim();
            int limit = 20;

            try {
                limit = Integer.parseInt(
                        rest.substring(sep + 1).trim()
                );
            } catch (NumberFormatException ignored) {
                // use default
            }

            List<String> history =
                    server.getPrivateHistory(username, otherUser, limit);

            for (String line : history) {
                sendMessage(line);
            }

        // ---- Mark room as read (/read_room <roomName>) -------------
        } else if (message.startsWith("/read_room ")) {

            String roomName = message.substring(11).trim();
            server.updateLastSeen(username);
            if (!roomName.isEmpty()) {
                server.broadcastReadRoom(username, roomName);
            }

        // ---- Mark PM as read (/read_pm <username>) -----------------
        } else if (message.startsWith("/read_pm ")) {

            String otherUser = message.substring(9).trim();
            server.updateLastSeen(username);
            if (!otherUser.isEmpty()) {
                server.sendReadPmReceipt(username, otherUser);
            }

        // ---- Unknown command ----------------------------------------
        } else {

            sendMessage("ERROR: Unknown command. "
                    + "Use /all, /msg, /room, /join, /leave, "
                    + "/list_rooms, /create_room, /kick, "
                    + "/typing_room, /typing_pm, /edit, /delete, "
                    + "/online, /history_room, /history_pm, "
                    + "/read_room, /read_pm, or /quit."
            );
        }
    }

    // ----------------------------------------------------------------
    // MESSAGE SENDING
    // ----------------------------------------------------------------

    /** Thread-safe message dispatch to this client. */
    public synchronized void sendMessage(String message) {

        if (output == null) {
            return;
        }

        try {

            output.write(message);
            output.newLine();
            output.flush();

        } catch (IOException e) {

            try {
                socket.close();
            } catch (IOException ignored) {
                // Socket cleanup handled in finally block.
            }
        }
    }

    // ----------------------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------------------

    private void cleanup() {

        if (registered) {

            server.removeClient(username, this);
            server.updateLastSeen(username);

            System.out.println(username + " disconnected.");

            server.broadcast(
                    "[SYSTEM] " + username + " left the chat.",
                    this
            );
            server.broadcast(
                    server.getOnlineUsersList(),
                    this
            );
        }

        try {

            socket.close();

        } catch (IOException e) {

            System.err.println(
                    "Error closing client socket: " + e.getMessage()
            );
        }
    }
}
