package com.example.chatterbox.service;

import com.example.chatterbox.dto.MessageDto;
import com.example.chatterbox.dto.PrivateChatDto;
import com.example.chatterbox.dto.RoomDto;
import com.example.chatterbox.dto.RoomMemberDto;
import com.example.chatterbox.model.ChatRoom;
import com.example.chatterbox.model.Message;
import com.example.chatterbox.model.PrivateChat;
import com.example.chatterbox.model.RoomMember;
import com.example.chatterbox.model.User;
import com.example.chatterbox.network.ChatServer;
import com.example.chatterbox.repository.ChatRoomRepository;
import com.example.chatterbox.repository.MessageRepository;
import com.example.chatterbox.repository.PrivateChatRepository;
import com.example.chatterbox.repository.RoomMemberRepository;
import com.example.chatterbox.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the REST API layer.
 * Handles room management, message history, and private chat
 * operations. All live message routing is owned by ChatServer / TCP.
 */
@Service
public class ChatService {

        private final UserRepository userRepository;
        private final ChatRoomRepository chatRoomRepository;
        private final RoomMemberRepository roomMemberRepository;
        private final MessageRepository messageRepository;
        private final PrivateChatRepository privateChatRepository;
        private final ChatServer chatServer;

        public ChatService(UserRepository userRepository,
                        ChatRoomRepository chatRoomRepository,
                        RoomMemberRepository roomMemberRepository,
                        MessageRepository messageRepository,
                        PrivateChatRepository privateChatRepository,
                        ChatServer chatServer) {

                this.userRepository = userRepository;
                this.chatRoomRepository = chatRoomRepository;
                this.roomMemberRepository = roomMemberRepository;
                this.messageRepository = messageRepository;
                this.privateChatRepository = privateChatRepository;
                this.chatServer = chatServer;
        }

        // ----------------------------------------------------------------
        // AUTH HELPERS
        // ----------------------------------------------------------------

        /**
         * Resolve the authenticated user from a session userId attribute.
         * Throws 401 if the userId is null or the user does not exist.
         */
        public User requireAuthenticatedUser(Long sessionUserId) {

                if (sessionUserId == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "You must be logged in");
                }

                return userRepository.findById(sessionUserId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Authenticated user not found"));
        }

        // ----------------------------------------------------------------
        // ROOM OPERATIONS
        // ----------------------------------------------------------------

        /** Return all chat rooms as DTOs. */
        public List<RoomDto> getAllRooms() {

                List<ChatRoom> rooms = chatRoomRepository.findAll();

                List<RoomDto> dtos = new ArrayList<>();

                for (ChatRoom room : rooms) {
                        dtos.add(toRoomDto(room));
                }

                return dtos;
        }

        /**
         * Create a new room via REST API.
         * The calling user becomes the OWNER member automatically.
         */
        public RoomDto createRoom(Long sessionUserId,
                        String roomName,
                        String description) {

                User creator = requireAuthenticatedUser(sessionUserId);

                if (roomName == null || roomName.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Room name is required");
                }

                String trimmedName = roomName.trim();

                if (chatRoomRepository.existsByRoomName(trimmedName)) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "A room with that name already exists");
                }

                ChatRoom room = new ChatRoom();
                room.setRoomName(trimmedName);
                room.setDescription(description != null ? description.trim() : "");
                room.setOwnerId(creator.getUserId());

                Long roomId = chatRoomRepository.save(room);

                // Add creator as OWNER member
                RoomMember ownerMember = new RoomMember();
                ownerMember.setRoomId(roomId);
                ownerMember.setUserId(creator.getUserId());
                ownerMember.setRole("OWNER");

                roomMemberRepository.save(ownerMember);

                return chatRoomRepository.findById(roomId)
                                .map(this::toRoomDto)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Room could not be retrieved after creation"));
        }

        /**
         * Return all members of a room, enriched with usernames.
         */
        public List<RoomMemberDto> getRoomMembers(Long roomId) {

                ChatRoom room = chatRoomRepository.findById(roomId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found"));

                List<RoomMember> members = roomMemberRepository.findByRoomId(room.getRoomId());

                List<RoomMemberDto> dtos = new ArrayList<>();

                for (RoomMember member : members) {

                        String username = userRepository
                                        .findById(member.getUserId())
                                        .map(User::getUsername)
                                        .orElse("unknown");

                        dtos.add(new RoomMemberDto(
                                        member.getRoomId(),
                                        member.getUserId(),
                                        username,
                                        member.getRole(),
                                        member.getJoinedAt()));
                }

                return dtos;
        }

        // ----------------------------------------------------------------
        // ROOM MESSAGE HISTORY
        // ----------------------------------------------------------------

        /**
         * Return the most recent {@code limit} messages for a room,
         * in chronological order, enriched with sender usernames.
         */
        public List<MessageDto> getRoomHistory(Long roomId, int limit) {

                ChatRoom room = chatRoomRepository.findById(roomId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found"));

                List<Message> messages = messageRepository.findRecentRoomMessages(
                                room.getRoomId(),
                                Math.max(1, Math.min(limit, 200)));

                return toMessageDtos(messages);
        }

        /**
         * Load older messages in a room before a given message ID
         * (for infinite-scroll pagination).
         */
        public List<MessageDto> getOlderRoomMessages(Long roomId,
                        Long beforeMessageId,
                        int limit) {

                ChatRoom room = chatRoomRepository.findById(roomId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Room not found"));

                List<Message> messages = messageRepository.findOlderRoomMessages(
                                room.getRoomId(),
                                beforeMessageId,
                                Math.max(1, Math.min(limit, 200)));

                return toMessageDtos(messages);
        }

        // ----------------------------------------------------------------
        // PRIVATE CHAT OPERATIONS
        // ----------------------------------------------------------------

        /**
         * Find or create a private chat between the authenticated user
         * and the specified target user, returning the PrivateChatDto.
         */
        public PrivateChatDto startPrivateChat(Long sessionUserId,
                        String targetUsername) {

                User self = requireAuthenticatedUser(sessionUserId);

                if (targetUsername == null || targetUsername.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Target username is required");
                }

                User target = userRepository
                                .findByUsername(targetUsername.trim())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "User not found: " + targetUsername));

                if (self.getUserId().equals(target.getUserId())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Cannot start a private chat with yourself");
                }

                Long privateChatId = privateChatRepository.save(
                                self.getUserId(),
                                target.getUserId());

                return privateChatRepository.findById(privateChatId)
                                .map(pc -> toPrivateChatDto(pc, self.getUserId()))
                                .orElseThrow(() -> new IllegalStateException(
                                                "Private chat could not be retrieved"));
        }

        /**
         * Return the most recent {@code limit} messages in a private chat,
         * in chronological order.
         * Caller must be a participant of the private chat.
         */
        public List<MessageDto> getPrivateChatHistory(Long sessionUserId,
                        Long privateChatId,
                        int limit) {

                requireAuthenticatedUser(sessionUserId);
                requirePrivateChatParticipant(sessionUserId, privateChatId);

                List<Message> messages = messageRepository.findRecentPrivateMessages(
                                privateChatId,
                                Math.max(1, Math.min(limit, 200)));

                return toMessageDtos(messages);
        }

        /**
         * Load older private messages before a given message ID.
         */
        public List<MessageDto> getOlderPrivateMessages(Long sessionUserId,
                        Long privateChatId,
                        Long beforeMessageId,
                        int limit) {

                requireAuthenticatedUser(sessionUserId);
                requirePrivateChatParticipant(sessionUserId, privateChatId);

                List<Message> messages = messageRepository.findOlderPrivateMessages(
                                privateChatId,
                                beforeMessageId,
                                Math.max(1, Math.min(limit, 200)));

                return toMessageDtos(messages);
        }

        /**
         * Return all private chats for the authenticated user,
         * with the other participant's username resolved.
         */
        public List<PrivateChatDto> listPrivateChats(Long sessionUserId) {

                requireAuthenticatedUser(sessionUserId);

                List<PrivateChat> chats = privateChatRepository.findByUserId(sessionUserId);

                List<PrivateChatDto> dtos = new ArrayList<>();

                for (PrivateChat pc : chats) {
                        dtos.add(toPrivateChatDto(pc, sessionUserId));
                }

                return dtos;
        }

        // ----------------------------------------------------------------
        // MESSAGE OPERATIONS
        // ----------------------------------------------------------------

        /**
         * Edit a message. Only the original sender may edit.
         * Throws 403 if the authenticated user is not the sender.
         */
        public MessageDto editMessage(Long sessionUserId,
                        Long messageId,
                        String newContent) {

                requireAuthenticatedUser(sessionUserId);

                if (newContent == null || newContent.isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "New content cannot be empty");
                }

                Message message = messageRepository.findById(messageId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Message not found"));

                if (!message.getSenderId().equals(sessionUserId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You can only edit your own messages");
                }

                if (message.getDeletedAt() != null) {
                        throw new ResponseStatusException(
                                        HttpStatus.GONE,
                                        "Cannot edit a deleted message");
                }

                int updated = messageRepository.updateMessage(
                                messageId,
                                sessionUserId,
                                newContent.trim());

                if (updated == 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Message could not be updated");
                }

                return messageRepository.findById(messageId)
                                .map(m -> toMessageDto(m))
                                .orElseThrow();
        }

        /**
         * Soft-delete a message. Only the original sender may delete.
         */
        public void deleteMessage(Long sessionUserId, Long messageId) {

                requireAuthenticatedUser(sessionUserId);

                Message message = messageRepository.findById(messageId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Message not found"));

                if (!message.getSenderId().equals(sessionUserId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You can only delete your own messages");
                }

                if (message.getDeletedAt() != null) {
                        throw new ResponseStatusException(
                                        HttpStatus.GONE,
                                        "Message is already deleted");
                }

                messageRepository.softDeleteMessage(messageId, sessionUserId);
        }

        // ----------------------------------------------------------------
        // PRIVATE HELPERS
        // ----------------------------------------------------------------

        /** Ensure that the given user is a participant in the given PC. */
        private void requirePrivateChatParticipant(Long userId,
                        Long privateChatId) {

                PrivateChat pc = privateChatRepository.findById(privateChatId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Private chat not found"));

                if (!pc.getUser1Id().equals(userId) &&
                                !pc.getUser2Id().equals(userId)) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You are not a participant of this chat");
                }
        }

        private RoomDto toRoomDto(ChatRoom room) {
                return new RoomDto(
                                room.getRoomId(),
                                room.getRoomName(),
                                room.getDescription(),
                                room.getOwnerId(),
                                room.getCreatedAt());
        }

        private PrivateChatDto toPrivateChatDto(PrivateChat pc,
                        Long selfUserId) {

                Long otherId = pc.getUser1Id().equals(selfUserId)
                                ? pc.getUser2Id()
                                : pc.getUser1Id();

                String otherUsername = userRepository.findById(otherId)
                                .map(User::getUsername)
                                .orElse("unknown");

                return new PrivateChatDto(
                                pc.getPrivateChatId(),
                                pc.getUser1Id(),
                                pc.getUser2Id(),
                                otherUsername,
                                pc.getCreatedAt());
        }

        private List<MessageDto> toMessageDtos(List<Message> messages) {

                List<MessageDto> dtos = new ArrayList<>();

                for (Message m : messages) {
                        dtos.add(toMessageDto(m));
                }

                return dtos;
        }

        private MessageDto toMessageDto(Message m) {

                String senderUsername = userRepository
                                .findById(m.getSenderId())
                                .map(User::getUsername)
                                .orElse("unknown");

                return new MessageDto(
                                m.getMessageId(),
                                m.getSenderId(),
                                senderUsername,
                                m.getRoomId(),
                                m.getPrivateChatId(),
                                m.getMessageContent(),
                                m.getCreatedAt(),
                                m.getUpdatedAt(),
                                m.getDeletedAt());
        }
}
