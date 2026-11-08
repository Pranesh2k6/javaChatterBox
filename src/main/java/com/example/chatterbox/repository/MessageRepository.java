package com.example.chatterbox.repository;

import com.example.chatterbox.model.Message;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert database row into Message object
    private final RowMapper<Message> messageRowMapper =
            new RowMapper<Message>() {

        @Override
        public Message mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            Timestamp created = rs.getTimestamp("created_at");
            Timestamp updated = rs.getTimestamp("updated_at");
            Timestamp deleted = rs.getTimestamp("deleted_at");

            long roomValue = rs.getLong("room_id");
            Long roomId = rs.wasNull() ? null : roomValue;

            long privateValue = rs.getLong("private_chat_id");
            Long privateChatId =
                    rs.wasNull() ? null : privateValue;

            return new Message(
                    rs.getLong("message_id"),
                    rs.getLong("sender_id"),
                    roomId,
                    privateChatId,
                    rs.getString("message_content"),
                    created != null ? created.toLocalDateTime() : null,
                    updated != null ? updated.toLocalDateTime() : null,
                    deleted != null ? deleted.toLocalDateTime() : null
            );
        }
    };

    // 1. SAVE MESSAGE
    public Long save(Message message) {

        if (message.getRoomId() != null
                == (message.getPrivateChatId() != null)) {

            throw new IllegalArgumentException(
                    "Message must belong to exactly one room "
                    + "or private chat"
            );
        }

        String sql = """
                INSERT INTO messages
                (sender_id, room_id, private_chat_id,
                 message_content)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, message.getSenderId());

            if (message.getRoomId() == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, message.getRoomId());
            }

            if (message.getPrivateChatId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, message.getPrivateChatId());
            }

            ps.setString(4, message.getMessageContent());

            return ps;

        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException(
                    "Failed to generate message ID"
            );
        }

        return keyHolder.getKey().longValue();
    }

    // 2. FIND MESSAGE BY ID
    public Optional<Message> findById(Long messageId) {

        String sql = """
                SELECT * FROM messages
                WHERE message_id = ?
                """;

        List<Message> messages = jdbcTemplate.query(
                sql,
                messageRowMapper,
                messageId
        );

        return messages.stream().findFirst();
    }

    // 3. GET ALL MESSAGES IN A ROOM
    public List<Message> findByRoomId(Long roomId) {

        String sql = """
                SELECT * FROM messages
                WHERE room_id = ?
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                roomId
        );
    }

    // 4. GET ALL MESSAGES IN A PRIVATE CHAT
    public List<Message> findByPrivateChatId(
            Long privateChatId) {

        String sql = """
                SELECT * FROM messages
                WHERE private_chat_id = ?
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                privateChatId
        );
    }

    // 5. GET LATEST N ROOM MESSAGES
    // Results are returned in chronological order.
    public List<Message> findRecentRoomMessages(
            Long roomId, int limit) {

        validateLimit(limit);

        String sql = """
                SELECT * FROM (
                    SELECT * FROM messages
                    WHERE room_id = ?
                    ORDER BY message_id DESC
                    LIMIT ?
                ) AS recent_messages
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                roomId,
                limit
        );
    }

    // 6. GET LATEST N PRIVATE MESSAGES
    public List<Message> findRecentPrivateMessages(
            Long privateChatId, int limit) {

        validateLimit(limit);

        String sql = """
                SELECT * FROM (
                    SELECT * FROM messages
                    WHERE private_chat_id = ?
                    ORDER BY message_id DESC
                    LIMIT ?
                ) AS recent_messages
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                privateChatId,
                limit
        );
    }

    // 7. LOAD OLDER ROOM MESSAGES
    public List<Message> findOlderRoomMessages(
            Long roomId, Long beforeMessageId, int limit) {

        validateLimit(limit);

        String sql = """
                SELECT * FROM (
                    SELECT * FROM messages
                    WHERE room_id = ?
                    AND message_id < ?
                    ORDER BY message_id DESC
                    LIMIT ?
                ) AS older_messages
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                roomId,
                beforeMessageId,
                limit
        );
    }

    // 8. LOAD OLDER PRIVATE MESSAGES
    public List<Message> findOlderPrivateMessages(
            Long privateChatId,
            Long beforeMessageId,
            int limit) {

        validateLimit(limit);

        String sql = """
                SELECT * FROM (
                    SELECT * FROM messages
                    WHERE private_chat_id = ?
                    AND message_id < ?
                    ORDER BY message_id DESC
                    LIMIT ?
                ) AS older_messages
                ORDER BY message_id ASC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                privateChatId,
                beforeMessageId,
                limit
        );
    }

    // 9. EDIT MESSAGE
    // Only the original sender can edit their message.
    public int updateMessage(
            Long messageId,
            Long senderId,
            String newContent) {

        String sql = """
                UPDATE messages
                SET message_content = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE message_id = ?
                AND sender_id = ?
                AND deleted_at IS NULL
                """;

        return jdbcTemplate.update(
                sql,
                newContent,
                messageId,
                senderId
        );
    }

    // 10. SOFT DELETE MESSAGE
    // Only the original sender can delete their message.
    public int softDeleteMessage(
            Long messageId, Long senderId) {

        String sql = """
                UPDATE messages
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE message_id = ?
                AND sender_id = ?
                AND deleted_at IS NULL
                """;

        return jdbcTemplate.update(
                sql,
                messageId,
                senderId
        );
    }

    // 11. GET MESSAGES SENT BY A USER
    public List<Message> findBySenderId(Long senderId) {

        String sql = """
                SELECT * FROM messages
                WHERE sender_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                messageRowMapper,
                senderId
        );
    }

    // Validate pagination limit
    private void validateLimit(int limit) {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Message limit must be greater than zero"
            );
        }
    }
}