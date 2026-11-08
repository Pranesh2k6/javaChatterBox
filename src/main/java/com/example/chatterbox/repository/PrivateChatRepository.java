package com.example.chatterbox.repository;

import com.example.chatterbox.model.PrivateChat;

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
public class PrivateChatRepository {

    private final JdbcTemplate jdbcTemplate;

    public PrivateChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert database row into PrivateChat object
    private final RowMapper<PrivateChat> privateChatRowMapper =
            new RowMapper<PrivateChat>() {

        @Override
        public PrivateChat mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            Timestamp created = rs.getTimestamp("created_at");

            return new PrivateChat(
                    rs.getLong("private_chat_id"),
                    rs.getLong("user1_id"),
                    rs.getLong("user2_id"),
                    created != null ? created.toLocalDateTime() : null
            );
        }
    };

    // 1. CREATE PRIVATE CHAT
    public Long save(Long firstUserId, Long secondUserId) {

        if (firstUserId == null || secondUserId == null
                || firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException(
                    "Two different valid user IDs are required"
            );
        }

        // Always store smaller ID first
        Long user1Id = Math.min(firstUserId, secondUserId);
        Long user2Id = Math.max(firstUserId, secondUserId);

        // Check whether conversation already exists
        Optional<PrivateChat> existing =
                findByParticipants(user1Id, user2Id);

        if (existing.isPresent()) {
            return existing.get().getPrivateChatId();
        }

        String sql = """
                INSERT INTO private_chats
                (user1_id, user2_id)
                VALUES (?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setLong(1, user1Id);
            ps.setLong(2, user2Id);

            return ps;

        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException(
                    "Failed to generate private chat ID"
            );
        }

        return keyHolder.getKey().longValue();
    }

    // 2. FIND PRIVATE CHAT BY ID
    public Optional<PrivateChat> findById(Long privateChatId) {

        String sql = """
                SELECT * FROM private_chats
                WHERE private_chat_id = ?
                """;

        List<PrivateChat> chats = jdbcTemplate.query(
                sql,
                privateChatRowMapper,
                privateChatId
        );

        return chats.stream().findFirst();
    }

    // 3. FIND CHAT BETWEEN TWO USERS
    public Optional<PrivateChat> findByParticipants(
            Long firstUserId, Long secondUserId) {

        if (firstUserId == null || secondUserId == null
                || firstUserId.equals(secondUserId)) {
            return Optional.empty();
        }

        Long user1Id = Math.min(firstUserId, secondUserId);
        Long user2Id = Math.max(firstUserId, secondUserId);

        String sql = """
                SELECT * FROM private_chats
                WHERE user1_id = ? AND user2_id = ?
                """;

        List<PrivateChat> chats = jdbcTemplate.query(
                sql,
                privateChatRowMapper,
                user1Id,
                user2Id
        );

        return chats.stream().findFirst();
    }

    // 4. GET ALL PRIVATE CHATS OF A USER
    public List<PrivateChat> findByUserId(Long userId) {

        String sql = """
                SELECT * FROM private_chats
                WHERE user1_id = ? OR user2_id = ?
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                privateChatRowMapper,
                userId,
                userId
        );
    }

    // 5. GET ALL PRIVATE CHATS
    public List<PrivateChat> findAll() {

        String sql = """
                SELECT * FROM private_chats
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(
                sql,
                privateChatRowMapper
        );
    }

    // 6. DELETE PRIVATE CHAT
    public int deleteById(Long privateChatId) {

        String sql = """
                DELETE FROM private_chats
                WHERE private_chat_id = ?
                """;

        return jdbcTemplate.update(sql, privateChatId);
    }
}