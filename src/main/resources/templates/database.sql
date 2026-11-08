CREATE DATABASE IF NOT EXISTS chat_application;
USE chat_application;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    room_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    owner_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS room_members (
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('OWNER', 'ADMIN', 'MEMBER') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS private_chats (
    private_chat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_private_chat UNIQUE (user1_id, user2_id),
    CONSTRAINT different_users CHECK (user1_id < user2_id),

    FOREIGN KEY (user1_id) REFERENCES users(user_id),
    FOREIGN KEY (user2_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    room_id BIGINT NULL,
    private_chat_id BIGINT NULL,
    message_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT valid_message_destination
        CHECK (
            (room_id IS NOT NULL AND private_chat_id IS NULL)
            OR
            (room_id IS NULL AND private_chat_id IS NOT NULL)
        ),

    FOREIGN KEY (sender_id) REFERENCES users(user_id),
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (private_chat_id) REFERENCES private_chats(private_chat_id) ON DELETE CASCADE
);

CREATE INDEX idx_messages_room
ON messages(room_id, message_id);

CREATE INDEX idx_messages_private_chat
ON messages(private_chat_id, message_id);

CREATE INDEX idx_messages_sender
ON messages(sender_id);

CREATE INDEX idx_users_username
ON users(username);

INSERT INTO chat_rooms (
    room_name,
    description,
    owner_id
) VALUES (
    'General',
    'Default public chat room',
    NULL
);

