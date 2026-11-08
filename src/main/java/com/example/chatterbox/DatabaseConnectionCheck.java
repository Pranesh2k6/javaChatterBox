
package com.example.chatterbox;

import com.example.chatterbox.model.User;
import com.example.chatterbox.model.ChatRoom;
import com.example.chatterbox.model.RoomMember;
import com.example.chatterbox.model.PrivateChat;
import com.example.chatterbox.model.Message;

import com.example.chatterbox.repository.UserRepository;
import com.example.chatterbox.repository.ChatRoomRepository;
import com.example.chatterbox.repository.RoomMemberRepository;
import com.example.chatterbox.repository.PrivateChatRepository;
import com.example.chatterbox.repository.MessageRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.repository-tests.enabled",
    havingValue = "true"
)
public class DatabaseConnectionCheck implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final PrivateChatRepository privateChatRepository;
    private final MessageRepository messageRepository;

    public DatabaseConnectionCheck(
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            RoomMemberRepository roomMemberRepository,
            PrivateChatRepository privateChatRepository,
            MessageRepository messageRepository) {

        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.privateChatRepository = privateChatRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void run(String... args) {

        try {

            System.out.println("\n================================");
            System.out.println("STARTING JDBC REPOSITORY TESTS");
            System.out.println("================================");

            // 1. USER REPOSITORY TEST
            String suffix = String.valueOf(System.currentTimeMillis());

            User user1 = new User();
            user1.setUsername("testuser1_" + suffix);
            user1.setPasswordHash("TEST_HASH_NOT_FOR_AUTHENTICATION");

            Long user1Id = userRepository.save(user1);

            User user2 = new User();
            user2.setUsername("testuser2_" + suffix);
            user2.setPasswordHash("TEST_HASH_NOT_FOR_AUTHENTICATION");

            Long user2Id = userRepository.save(user2);

            System.out.println("1. USER INSERT: SUCCESS");

            User retrievedUser = userRepository.findById(user1Id)
                    .orElseThrow(() ->
                            new RuntimeException("User retrieval failed"));

            System.out.println("   USER RETRIEVAL: SUCCESS");
            System.out.println("   Username: " + retrievedUser.getUsername());

            if (!userRepository.existsByUsername(user1.getUsername())) {
                throw new RuntimeException("Username existence test failed");
            }

            System.out.println("   USERNAME CHECK: SUCCESS");


            // 2. CHAT ROOM REPOSITORY TEST
            ChatRoom room = new ChatRoom();
            room.setRoomName("TestRoom_" + suffix);
            room.setDescription("Temporary JDBC testing room");
            room.setOwnerId(user1Id);

            Long roomId = chatRoomRepository.save(room);

            System.out.println("2. CHAT ROOM INSERT: SUCCESS");

            ChatRoom retrievedRoom = chatRoomRepository.findById(roomId)
                    .orElseThrow(() ->
                            new RuntimeException("Room retrieval failed"));

            System.out.println("   ROOM RETRIEVAL: SUCCESS");
            System.out.println("   Room: " + retrievedRoom.getRoomName());


            // 3. ROOM MEMBER REPOSITORY TEST
            RoomMember member1 = new RoomMember();
            member1.setRoomId(roomId);
            member1.setUserId(user1Id);
            member1.setRole("OWNER");

            roomMemberRepository.save(member1);

            RoomMember member2 = new RoomMember();
            member2.setRoomId(roomId);
            member2.setUserId(user2Id);
            member2.setRole("MEMBER");

            roomMemberRepository.save(member2);

            System.out.println("3. ROOM MEMBER INSERT: SUCCESS");

            if (!roomMemberRepository.exists(roomId, user2Id)) {
                throw new RuntimeException("Membership test failed");
            }

            System.out.println("   MEMBERSHIP CHECK: SUCCESS");
            System.out.println("   Member count: "
                    + roomMemberRepository.countMembers(roomId));


            // 4. PRIVATE CHAT REPOSITORY TEST
            Long privateChatId =
                    privateChatRepository.save(user1Id, user2Id);

            System.out.println("4. PRIVATE CHAT INSERT: SUCCESS");

            PrivateChat retrievedChat =
                    privateChatRepository.findById(privateChatId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Private chat retrieval failed"));

            System.out.println("   PRIVATE CHAT RETRIEVAL: SUCCESS");

            Long sameChatId =
                    privateChatRepository.save(user2Id, user1Id);

            if (!privateChatId.equals(sameChatId)) {
                throw new RuntimeException(
                        "Duplicate private chat was created");
            }

            System.out.println("   DUPLICATE CHAT PREVENTION: SUCCESS");


            // 5. MESSAGE REPOSITORY TEST
            Message roomMessage = new Message();
            roomMessage.setSenderId(user1Id);
            roomMessage.setRoomId(roomId);
            roomMessage.setPrivateChatId(null);
            roomMessage.setMessageContent("Hello from JDBC testing!");

            Long roomMessageId = messageRepository.save(roomMessage);

            System.out.println("5. ROOM MESSAGE INSERT: SUCCESS");

            Message retrievedMessage =
                    messageRepository.findById(roomMessageId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Message retrieval failed"));

            System.out.println("   MESSAGE RETRIEVAL: SUCCESS");
            System.out.println("   Content: "
                    + retrievedMessage.getMessageContent());


            // TEST MESSAGE EDIT
            int updated = messageRepository.updateMessage(
                    roomMessageId,
                    user1Id,
                    "Updated JDBC test message"
            );

            if (updated != 1) {
                throw new RuntimeException("Message update failed");
            }

            System.out.println("   MESSAGE UPDATE: SUCCESS");


            // TEST MESSAGE HISTORY
            var history = messageRepository.findRecentRoomMessages(
                    roomId, 10
            );

            if (history.isEmpty()) {
                throw new RuntimeException("Message history test failed");
            }

            System.out.println("   MESSAGE HISTORY: SUCCESS");


            // TEST PRIVATE MESSAGE
            Message privateMessage = new Message();
            privateMessage.setSenderId(user1Id);
            privateMessage.setPrivateChatId(privateChatId);
            privateMessage.setRoomId(null);
            privateMessage.setMessageContent("Hello from private chat!");

            Long privateMessageId = messageRepository.save(privateMessage);

            messageRepository.findById(privateMessageId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Private message retrieval failed"));

            System.out.println("   PRIVATE MESSAGE: SUCCESS");


            // TEST SOFT DELETE
            int deleted = messageRepository.softDeleteMessage(
                    roomMessageId, user1Id
            );

            if (deleted != 1) {
                throw new RuntimeException("Message soft delete failed");
            }

            System.out.println("   MESSAGE SOFT DELETE: SUCCESS");


            System.out.println("\n================================");
            System.out.println("ALL JDBC REPOSITORY TESTS PASSED!");
            System.out.println("================================");

            System.out.println("Test User 1 ID: " + user1Id);
            System.out.println("Test User 2 ID: " + user2Id);
            System.out.println("Test Room ID: " + roomId);
            System.out.println("Private Chat ID: " + privateChatId);

        } catch (Exception e) {

            System.err.println("\nJDBC REPOSITORY TEST FAILED!");
            e.printStackTrace();

            throw new IllegalStateException(
                    "Repository testing failed", e
            );
        }
    }
}
