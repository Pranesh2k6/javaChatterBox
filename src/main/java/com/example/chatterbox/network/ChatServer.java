
package com.example.chatterbox.network;

import com.example.chatterbox.model.ChatRoom;
import com.example.chatterbox.model.Message;
import com.example.chatterbox.model.PrivateChat;
import com.example.chatterbox.model.RoomMember;
import com.example.chatterbox.model.User;
import com.example.chatterbox.repository.ChatRoomRepository;
import com.example.chatterbox.repository.MessageRepository;
import com.example.chatterbox.repository.PrivateChatRepository;
import com.example.chatterbox.repository.RoomMemberRepository;
import com.example.chatterbox.repository.UserRepository;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP chat server running on port 9000.
 * Owns all live message routing and delivery.
 * Spring Boot REST controllers call read-only query methods here
 * for online-status checks; actual routing always goes via TCP.
 */
@Component
public class ChatServer {

    private static final int PORT = 9000;
    private static final int MAX_CLIENTS = 100;

    // JDBC repositories managed by Spring
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PrivateChatRepository privateChatRepository;
    private final RoomMemberRepository roomMemberRepository;

    // Thread pool for handling TCP clients
    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(MAX_CLIENTS);

    // username → handler map; all access is synchronized on this map
    private final Map<String, ClientHandler> clients =
            Collections.synchronizedMap(new HashMap<>());

    private volatile ServerSocket serverSocket;
    private volatile boolean running = false;

    // Constructor injection
    public ChatServer(
            MessageRepository messageRepository,
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            PrivateChatRepository privateChatRepository,
            RoomMemberRepository roomMemberRepository) {

        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.privateChatRepository = privateChatRepository;
        this.roomMemberRepository = roomMemberRepository;
    }

    // ----------------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------------

    /** Start TCP server after Spring Boot finishes initialising. */
    @EventListener(ApplicationReadyEvent.class)
    public void startServer() {

        Thread serverThread = new Thread(this::start, "chat-tcp-server");
        serverThread.start();
    }

    /** Main TCP accept loop. */
    public void start() {

        running = true;

        System.out.println("Starting Chat Server...");

        try {

            serverSocket = new ServerSocket(PORT);

            System.out.println("Chat Server started!");
            System.out.println("Listening on port: " + PORT);
            System.out.println("Waiting for clients...\n");

            while (running) {

                Socket clientSocket = serverSocket.accept();

                System.out.println(
                        "New connection: " +
                        clientSocket.getInetAddress()
                );

                ClientHandler handler =
                        new ClientHandler(clientSocket, this);

                threadPool.execute(handler);
            }

        } catch (IOException e) {

            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }

        } finally {

            running = false;
            threadPool.shutdown();
            System.out.println("Chat Server stopped.");
        }
    }

    /** Graceful shutdown called by Spring on application close. */
    @PreDestroy
    public void stopServer() {

        running = false;

        try {

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

        } catch (IOException e) {

            System.err.println(
                    "Error closing server: " + e.getMessage()
            );
        }

        threadPool.shutdownNow();

        System.out.println("TCP server shutdown requested.");
    }

    // ----------------------------------------------------------------
    // CLIENT REGISTRATION
    // ----------------------------------------------------------------

    /** Register a newly connected client. Returns false if username taken. */
    public boolean registerClient(String username, ClientHandler handler) {

        synchronized (clients) {

            if (clients.containsKey(username)) {
                return false;
            }

            clients.put(username, handler);
            return true;
        }
    }

    /** Remove a client on disconnect. */
    public void removeClient(String username, ClientHandler handler) {

        synchronized (clients) {

            if (clients.get(username) == handler) {
                clients.remove(username);
            }
        }
    }

    // ----------------------------------------------------------------
    // ONLINE STATUS
    // ----------------------------------------------------------------

    /** True if the given username is currently connected via TCP. */
    public boolean isOnline(String username) {

        synchronized (clients) {
            return clients.containsKey(username);
        }
    }

    /** Snapshot of all currently connected usernames. */
    public Set<String> getOnlineUsernames() {

        synchronized (clients) {
            return new HashSet<>(clients.keySet());
        }
    }

    /** Formatted online-users list string for TCP protocol. */
    public String getOnlineUsersList() {

        synchronized (clients) {

            if (clients.isEmpty()) {
                return "[ONLINE] (nobody online)";
            }

            return "[ONLINE] " + String.join(",", clients.keySet());
        }
    }

    // ----------------------------------------------------------------
    // BROADCAST / ROUTING
    // ----------------------------------------------------------------

    /** Broadcast a message to ALL connected clients. */
    public void broadcast(String message, ClientHandler sender) {

        ClientHandler[] connectedClients;

        synchronized (clients) {
            connectedClients =
                    clients.values().toArray(new ClientHandler[0]);
        }

        for (ClientHandler client : connectedClients) {
            client.sendMessage(message);
        }
    }

    /**
     * Broadcast a message to all online members of a specific room.
     * Uses room_members table to find who belongs to the room.
     */
    public void broadcastToRoom(String roomName,
                                String message,
                                ClientHandler sender) {

        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElse(null);

        if (room == null) {
            return;
        }

        List<RoomMember> members =
                roomMemberRepository.findByRoomId(room.getRoomId());

        synchronized (clients) {

            for (RoomMember member : members) {

                userRepository.findById(member.getUserId())
                        .ifPresent(user -> {

                            ClientHandler handler =
                                    clients.get(user.getUsername());

                            if (handler != null) {
                                handler.sendMessage(message);
                            }
                        });
            }
        }
    }

    /** Route a private message directly to a named client. */
    public boolean sendPrivateMessage(String receiver, String message) {

        ClientHandler recipient;

        synchronized (clients) {
            recipient = clients.get(receiver);
        }

        if (recipient == null) {
            return false;
        }

        recipient.sendMessage(message);
        return true;
    }

    // ----------------------------------------------------------------
    // DATABASE – SAVE MESSAGES
    // ----------------------------------------------------------------

    /**
     * Save a message to the General (room_id=1) chat room.
     * Called by the /all command.
     */
    public Long saveGeneralMessage(String username, String content) {

        User sender = requireUser(username);
        requireActive(sender);

        ChatRoom generalRoom = chatRoomRepository.findByName("General")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "General room does not exist."
                        )
                );

        Message message = new Message();
        message.setSenderId(sender.getUserId());
        message.setRoomId(generalRoom.getRoomId());
        message.setPrivateChatId(null);
        message.setMessageContent(content);

        return messageRepository.save(message);
    }

    /**
     * Save a message to the specified named room.
     * Validates that the sender is a member of the room.
     */
    public Long saveRoomMessage(String username,
                                String roomName,
                                String content) {

        User sender = requireUser(username);
        requireActive(sender);

        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Room does not exist: " + roomName
                        )
                );

        // Sender must be a member
        if (!roomMemberRepository.exists(
                room.getRoomId(), sender.getUserId())) {

            throw new IllegalStateException(
                    "You are not a member of room: " + roomName
            );
        }

        Message message = new Message();
        message.setSenderId(sender.getUserId());
        message.setRoomId(room.getRoomId());
        message.setPrivateChatId(null);
        message.setMessageContent(content);

        return messageRepository.save(message);
    }

    /**
     * Find or create a private_chats record for the two users,
     * then save the message.
     * Returns the new message ID.
     */
    public Long savePrivateMessage(String senderUsername,
                                   String receiverUsername,
                                   String content) {

        User sender = requireUser(senderUsername);
        requireActive(sender);

        User receiver = userRepository
                .findByUsername(receiverUsername)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User does not exist: " + receiverUsername
                        )
                );

        // Find or create private chat record
        Long privateChatId = privateChatRepository.save(
                sender.getUserId(),
                receiver.getUserId()
        );

        Message message = new Message();
        message.setSenderId(sender.getUserId());
        message.setRoomId(null);
        message.setPrivateChatId(privateChatId);
        message.setMessageContent(content);

        return messageRepository.save(message);
    }

    // ----------------------------------------------------------------
    // ROOM MANAGEMENT
    // ----------------------------------------------------------------

    /**
     * Join an existing room.
     * If already a member, returns a notice instead of an error.
     * On success, broadcasts a join notification to room members.
     */
    public String joinRoom(String username, String roomName) {

        User user = requireUser(username);

        Optional<ChatRoom> roomOpt =
                chatRoomRepository.findByName(roomName);

        if (roomOpt.isEmpty()) {
            return "ERROR: Room does not exist: " + roomName;
        }

        ChatRoom room = roomOpt.get();

        if (roomMemberRepository.exists(
                room.getRoomId(), user.getUserId())) {

            return "[SYSTEM] You are already a member of " + roomName;
        }

        RoomMember member = new RoomMember();
        member.setRoomId(room.getRoomId());
        member.setUserId(user.getUserId());
        member.setRole("MEMBER");

        roomMemberRepository.save(member);

        broadcastToRoom(
                roomName,
                "[SYSTEM] " + username + " joined " + roomName,
                null
        );

        return "[SYSTEM] Joined room: " + roomName;
    }

    /**
     * Leave a room.
     * If the leaving user is the OWNER, ownership transfers to the next member
     * (or becomes null if no members remain).
     */
    public String leaveRoom(String username, String roomName) {

        if ("General".equalsIgnoreCase(roomName)) {
            return "ERROR: You cannot leave the General room.";
        }

        User user = requireUser(username);

        Optional<ChatRoom> roomOpt =
                chatRoomRepository.findByName(roomName);

        if (roomOpt.isEmpty()) {
            return "ERROR: Room does not exist: " + roomName;
        }

        ChatRoom room = roomOpt.get();

        Optional<RoomMember> memberOpt =
                roomMemberRepository.findByRoomIdAndUserId(
                        room.getRoomId(), user.getUserId()
                );

        if (memberOpt.isEmpty()) {
            return "ERROR: You are not a member of " + roomName;
        }

        boolean wasOwner = "OWNER".equalsIgnoreCase(memberOpt.get().getRole())
                || (room.getOwnerId() != null && room.getOwnerId().equals(user.getUserId()));

        roomMemberRepository.delete(room.getRoomId(), user.getUserId());

        if (wasOwner) {
            List<RoomMember> remaining = roomMemberRepository.findByRoomId(room.getRoomId());
            if (!remaining.isEmpty()) {
                RoomMember nextOwner = remaining.get(0);
                roomMemberRepository.updateRole(room.getRoomId(), nextOwner.getUserId(), "OWNER");
                chatRoomRepository.updateOwner(room.getRoomId(), nextOwner.getUserId());
                userRepository.findById(nextOwner.getUserId()).ifPresent(newOwnerUser ->
                        broadcastToRoom(
                                roomName,
                                "[SYSTEM] " + newOwnerUser.getUsername() + " is now the owner of " + roomName,
                                null
                        )
                );
            } else {
                chatRoomRepository.updateOwner(room.getRoomId(), null);
            }
        }

        sendPrivateMessage(
                username,
                "[LEFT_ROOM] " + roomName + ":" + username
        );

        broadcastToRoom(
                roomName,
                "[SYSTEM] " + username + " left " + roomName,
                null
        );

        return "[SYSTEM] You left room: " + roomName;
    }

    /**
     * Create a new room via TCP command.
     * The user becomes the OWNER member automatically.
     */
    public String createRoom(String username, String roomName) {

        User user = requireUser(username);

        if (chatRoomRepository.existsByRoomName(roomName)) {
            return "ERROR: Room already exists: " + roomName;
        }

        ChatRoom room = new ChatRoom();
        room.setRoomName(roomName);
        room.setDescription("");
        room.setOwnerId(user.getUserId());

        Long roomId = chatRoomRepository.save(room);

        RoomMember owner = new RoomMember();
        owner.setRoomId(roomId);
        owner.setUserId(user.getUserId());
        owner.setRole("OWNER");

        roomMemberRepository.save(owner);

        // Notify all online users about the new room
        broadcast(
                "[SYSTEM] New room created: " + roomName
                + " (by " + username + ")",
                null
        );

        return "[SYSTEM] Room created: " + roomName;
    }

    /**
     * Return a formatted list of all rooms.
     */
    public String listRooms() {

        List<ChatRoom> rooms = chatRoomRepository.findAll();

        if (rooms.isEmpty()) {
            return "[SYSTEM] No rooms available.";
        }

        StringBuilder sb = new StringBuilder("[SYSTEM] Rooms: ");

        for (int i = 0; i < rooms.size(); i++) {

            sb.append(rooms.get(i).getRoomName());

            if (i < rooms.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Kick a member from a room.
     * The requester must be OWNER or ADMIN.
     * OWNER cannot be kicked.
     */
    public String kickMember(String requesterUsername,
                             String roomName,
                             String targetUsername) {

        User requester = requireUser(requesterUsername);

        String cleanTarget = targetUsername != null && targetUsername.startsWith("@")
                ? targetUsername.substring(1).trim()
                : targetUsername;

        Optional<ChatRoom> roomOpt =
                chatRoomRepository.findByName(roomName);

        if (roomOpt.isEmpty()) {
            return "ERROR: Room does not exist: " + roomName;
        }

        ChatRoom room = roomOpt.get();

        // Validate requester's role (check both chat_rooms.owner_id and room_members.role)
        Optional<RoomMember> requesterMember =
                roomMemberRepository.findByRoomIdAndUserId(
                        room.getRoomId(), requester.getUserId()
                );

        boolean isRoomOwner = room.getOwnerId() != null
                && room.getOwnerId().equals(requester.getUserId());
        String requesterRole = requesterMember.map(RoomMember::getRole).orElse("");

        if (!isRoomOwner &&
                !"OWNER".equalsIgnoreCase(requesterRole) &&
                !"ADMIN".equalsIgnoreCase(requesterRole)) {

            return "ERROR: Only OWNER or ADMIN can kick members.";
        }

        // Find target user
        Optional<User> targetOpt =
                userRepository.findByUsername(cleanTarget);

        if (targetOpt.isEmpty()) {
            return "ERROR: User does not exist: " + cleanTarget;
        }

        User target = targetOpt.get();

        if (target.getUserId().equals(requester.getUserId())) {
            return "ERROR: You cannot kick yourself. Use Leave Room instead.";
        }

        Optional<RoomMember> targetMember =
                roomMemberRepository.findByRoomIdAndUserId(
                        room.getRoomId(), target.getUserId()
                );

        if (targetMember.isEmpty()) {
            return "ERROR: " + cleanTarget
                    + " is not a member of " + roomName;
        }

        boolean targetIsOwner = "OWNER".equalsIgnoreCase(targetMember.get().getRole())
                || (room.getOwnerId() != null && room.getOwnerId().equals(target.getUserId()));

        if (targetIsOwner) {
            return "ERROR: Cannot kick the room owner.";
        }

        roomMemberRepository.delete(room.getRoomId(), target.getUserId());

        String kickedEvent = "[KICKED] " + roomName + ":" + cleanTarget;

        // Notify the kicked user if online
        sendPrivateMessage(cleanTarget, kickedEvent);
        sendPrivateMessage(
                cleanTarget,
                "[SYSTEM] You were kicked from " + roomName
                + " by " + requesterUsername
        );

        broadcastToRoom(roomName, kickedEvent, null);
        broadcastToRoom(
                roomName,
                "[SYSTEM] " + cleanTarget + " was kicked from "
                + roomName + " by " + requesterUsername,
                null
        );

        return "[SYSTEM] " + cleanTarget
                + " has been kicked from " + roomName;
    }

    // ----------------------------------------------------------------
    // MESSAGE HISTORY
    // ----------------------------------------------------------------

    /**
     * Return recent messages for a room, formatted for TCP protocol.
     * Validates the caller is a room member.
     */
    public List<String> getRoomHistory(String username,
                                        String roomName,
                                        int limit) {

        List<String> lines = new ArrayList<>();

        User user = requireUser(username);

        Optional<ChatRoom> roomOpt =
                chatRoomRepository.findByName(roomName);

        if (roomOpt.isEmpty()) {
            lines.add("ERROR: Room does not exist: " + roomName);
            return lines;
        }

        ChatRoom room = roomOpt.get();

        if (!roomMemberRepository.exists(
                room.getRoomId(), user.getUserId())) {

            lines.add("ERROR: You are not a member of " + roomName);
            return lines;
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<Message> messages =
                messageRepository.findRecentRoomMessages(
                        room.getRoomId(), safeLimit
                );

        lines.add("[HISTORY] --- " + roomName
                + " (" + messages.size() + " messages) ---");

        for (Message msg : messages) {

            String sender = userRepository
                    .findById(msg.getSenderId())
                    .map(User::getUsername)
                    .orElse("unknown");

            String line = "[HISTORY] [" + msg.getCreatedAt() + "] "
                    + sender + ": " + msg.getMessageContent();

            if (msg.getDeletedAt() != null) {
                line = "[HISTORY] [" + msg.getCreatedAt() + "] "
                        + "[DELETED]";
            }

            lines.add(line);
        }

        lines.add("[HISTORY] --- end ---");

        return lines;
    }

    /**
     * Return recent private messages between this user and another,
     * formatted for TCP protocol.
     */
    public List<String> getPrivateHistory(String username,
                                           String otherUsername,
                                           int limit) {

        List<String> lines = new ArrayList<>();

        User user = requireUser(username);

        Optional<User> otherOpt =
                userRepository.findByUsername(otherUsername);

        if (otherOpt.isEmpty()) {
            lines.add("ERROR: User does not exist: " + otherUsername);
            return lines;
        }

        User other = otherOpt.get();

        Optional<PrivateChat> pcOpt =
                privateChatRepository.findByParticipants(
                        user.getUserId(), other.getUserId()
                );

        if (pcOpt.isEmpty()) {
            lines.add("[HISTORY] No conversation with " + otherUsername);
            return lines;
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<Message> messages =
                messageRepository.findRecentPrivateMessages(
                        pcOpt.get().getPrivateChatId(), safeLimit
                );

        lines.add("[HISTORY] --- PM with " + otherUsername
                + " (" + messages.size() + " messages) ---");

        for (Message msg : messages) {

            String sender = userRepository
                    .findById(msg.getSenderId())
                    .map(User::getUsername)
                    .orElse("unknown");

            String line = "[HISTORY] [" + msg.getCreatedAt() + "] "
                    + sender + ": " + msg.getMessageContent();

            if (msg.getDeletedAt() != null) {
                line = "[HISTORY] [" + msg.getCreatedAt() + "] "
                        + "[DELETED]";
            }

            lines.add(line);
        }

        lines.add("[HISTORY] --- end ---");

        return lines;
    }

    // ----------------------------------------------------------------
    // MESSAGE EDIT / DELETE
    // ----------------------------------------------------------------

    /**
     * Edit a message. Only the original sender may edit.
     * Broadcasts an [EDIT] notification to relevant room/PM participants.
     */
    public String editMessage(String username,
                               Long messageId,
                               String newContent) {

        User user = requireUser(username);

        Optional<Message> msgOpt = messageRepository.findById(messageId);

        if (msgOpt.isEmpty()) {
            return "ERROR: Message not found: " + messageId;
        }

        Message msg = msgOpt.get();

        if (!msg.getSenderId().equals(user.getUserId())) {
            return "ERROR: You can only edit your own messages.";
        }

        if (msg.getDeletedAt() != null) {
            return "ERROR: Cannot edit a deleted message.";
        }

        int updated = messageRepository.updateMessage(
                messageId, user.getUserId(), newContent
        );

        if (updated == 0) {
            return "ERROR: Message could not be updated.";
        }

        String editNotification = "[EDIT] " + messageId
                + ":" + newContent;

        // Broadcast edit notification to the right audience
        if (msg.getRoomId() != null) {

            chatRoomRepository.findById(msg.getRoomId())
                    .ifPresent(room ->
                            broadcastToRoom(
                                    room.getRoomName(),
                                    editNotification,
                                    null
                            )
                    );

        } else if (msg.getPrivateChatId() != null) {

            privateChatRepository.findById(msg.getPrivateChatId())
                    .ifPresent(pc -> {

                        // notify the other participant
                        userRepository.findById(
                                pc.getUser1Id().equals(user.getUserId())
                                        ? pc.getUser2Id()
                                        : pc.getUser1Id()
                        ).ifPresent(other ->
                                sendPrivateMessage(
                                        other.getUsername(),
                                        editNotification
                                )
                        );
                    });
        }

        return "[SYSTEM] Message " + messageId + " updated.";
    }

    /**
     * Soft-delete a message. Only the original sender may delete.
     * Broadcasts a [DELETE] notification.
     */
    public String deleteMessage(String username, Long messageId) {

        User user = requireUser(username);

        Optional<Message> msgOpt = messageRepository.findById(messageId);

        if (msgOpt.isEmpty()) {
            return "ERROR: Message not found: " + messageId;
        }

        Message msg = msgOpt.get();

        if (!msg.getSenderId().equals(user.getUserId())) {
            return "ERROR: You can only delete your own messages.";
        }

        if (msg.getDeletedAt() != null) {
            return "ERROR: Message is already deleted.";
        }

        messageRepository.softDeleteMessage(messageId, user.getUserId());

        String deleteNotification = "[DELETE] " + messageId;

        if (msg.getRoomId() != null) {

            chatRoomRepository.findById(msg.getRoomId())
                    .ifPresent(room ->
                            broadcastToRoom(
                                    room.getRoomName(),
                                    deleteNotification,
                                    null
                            )
                    );

        } else if (msg.getPrivateChatId() != null) {

            privateChatRepository.findById(msg.getPrivateChatId())
                    .ifPresent(pc -> {

                        userRepository.findById(
                                pc.getUser1Id().equals(user.getUserId())
                                        ? pc.getUser2Id()
                                        : pc.getUser1Id()
                        ).ifPresent(other ->
                                sendPrivateMessage(
                                        other.getUsername(),
                                        deleteNotification
                                )
                        );
                    });
        }

        return "[SYSTEM] Message " + messageId + " deleted.";
    }

    // ----------------------------------------------------------------
    // TYPING INDICATORS
    // ----------------------------------------------------------------

    /**
     * Broadcast a typing indicator to all online members of a room,
     * except the typer themselves.
     */
    public void broadcastTypingRoom(String username, String roomName) {

        // General is a broadcast-to-everyone room (see /all), so its
        // typing indicator goes to every connected client, not just members.
        if ("General".equalsIgnoreCase(roomName)) {

            String notification = "[TYPING] " + username + " in " + roomName;
            ClientHandler[] connected;

            synchronized (clients) {
                connected = clients.entrySet().stream()
                        .filter(e -> !e.getKey().equals(username))
                        .map(Map.Entry::getValue)
                        .toArray(ClientHandler[]::new);
            }

            for (ClientHandler handler : connected) {
                handler.sendMessage(notification);
            }

            return;
        }

        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElse(null);

        if (room == null) {
            return;
        }

        List<RoomMember> members =
                roomMemberRepository.findByRoomId(room.getRoomId());

        String notification =
                "[TYPING] " + username + " in " + roomName;

        synchronized (clients) {

            for (RoomMember member : members) {

                userRepository.findById(member.getUserId())
                        .ifPresent(user -> {

                            // Don't echo back to the typer
                            if (!user.getUsername().equals(username)) {

                                ClientHandler handler =
                                        clients.get(user.getUsername());

                                if (handler != null) {
                                    handler.sendMessage(notification);
                                }
                            }
                        });
            }
        }
    }

    /**
     * Send a typing indicator to a specific user for a PM conversation.
     */
    public void sendTypingPm(String senderUsername,
                              String receiverUsername) {

        sendPrivateMessage(
                receiverUsername,
                "[TYPING_PM] " + senderUsername
        );
    }

    /**
     * Send a real-time read receipt to a specific user for a PM conversation.
     */
    public void sendReadPmReceipt(String readerUsername,
                                  String senderUsername) {

        sendPrivateMessage(
                senderUsername,
                "[READ_PM] " + readerUsername
        );
    }

    /**
     * Broadcast a real-time read receipt to all online members of a room
     * except the reader themselves.
     */
    public void broadcastReadRoom(String readerUsername, String roomName) {

        String notification = "[READ_ROOM] " + roomName + ":" + readerUsername;

        if ("General".equalsIgnoreCase(roomName)) {

            ClientHandler[] connected;

            synchronized (clients) {
                connected = clients.entrySet().stream()
                        .filter(e -> !e.getKey().equals(readerUsername))
                        .map(Map.Entry::getValue)
                        .toArray(ClientHandler[]::new);
            }

            for (ClientHandler handler : connected) {
                handler.sendMessage(notification);
            }

            return;
        }

        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElse(null);

        if (room == null) {
            return;
        }

        List<RoomMember> members =
                roomMemberRepository.findByRoomId(room.getRoomId());

        synchronized (clients) {

            for (RoomMember member : members) {

                userRepository.findById(member.getUserId())
                        .ifPresent(user -> {

                            if (!user.getUsername().equals(readerUsername)) {

                                ClientHandler handler =
                                        clients.get(user.getUsername());

                                if (handler != null) {
                                    handler.sendMessage(notification);
                                }
                            }
                        });
            }
        }
    }

    // ----------------------------------------------------------------
    // LAST SEEN
    // ----------------------------------------------------------------

    /**
     * Update the last_seen timestamp for the given username.
     * Called on disconnect and on /read_room, /read_pm commands.
     */
    public void updateLastSeen(String username) {

        userRepository.findByUsername(username)
                .ifPresent(user ->
                        userRepository.updateLastSeen(user.getUserId())
                );
    }

    // ----------------------------------------------------------------
    // PRIVATE HELPERS
    // ----------------------------------------------------------------

    private User requireUser(String username) {

        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User does not exist: " + username
                        )
                );
    }

    private void requireActive(User user) {

        if (!user.isActive()) {
            throw new IllegalStateException(
                    "This user account is inactive."
            );
        }
    }
}
