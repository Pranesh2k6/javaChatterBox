package com.example.chatterbox.repository;

import com.example.chatterbox.model.ChatRoom;

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
public class ChatRoomRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert database row into ChatRoom object
    private final RowMapper<ChatRoom> chatRoomRowMapper =
            new RowMapper<ChatRoom>() {

        @Override
        public ChatRoom mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            Timestamp created = rs.getTimestamp("created_at");

            long ownerValue = rs.getLong("owner_id");
            Long ownerId = rs.wasNull() ? null : ownerValue;

            return new ChatRoom(
                    rs.getLong("room_id"),
                    rs.getString("room_name"),
                    rs.getString("description"),
                    ownerId,
                    created != null ? created.toLocalDateTime() : null
            );
        }
    };

    // 1. CREATE ROOM
    public Long save(ChatRoom room) {

        String sql = """
                INSERT INTO chat_rooms
                (room_name, description, owner_id)
                VALUES (?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            ps.setString(1, room.getRoomName());
            ps.setString(2, room.getDescription());

            if (room.getOwnerId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, room.getOwnerId());
            }

            return ps;

        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException(
                    "Failed to generate room ID"
            );
        }

        return keyHolder.getKey().longValue();
    }

    // 2. FIND ROOM BY ID
    public Optional<ChatRoom> findById(Long roomId) {

        String sql = """
                SELECT * FROM chat_rooms
                WHERE room_id = ?
                """;

        List<ChatRoom> rooms = jdbcTemplate.query(
                sql,
                chatRoomRowMapper,
                roomId
        );

        return rooms.stream().findFirst();
    }

    // 3. FIND ROOM BY NAME
    public Optional<ChatRoom> findByName(String roomName) {

        String sql = """
                SELECT * FROM chat_rooms
                WHERE room_name = ?
                """;

        List<ChatRoom> rooms = jdbcTemplate.query(
                sql,
                chatRoomRowMapper,
                roomName
        );

        return rooms.stream().findFirst();
    }

    // 4. GET ALL ROOMS
    public List<ChatRoom> findAll() {

        String sql = """
                SELECT * FROM chat_rooms
                ORDER BY room_id
                """;

        return jdbcTemplate.query(sql, chatRoomRowMapper);
    }

    // 5. UPDATE ROOM
    public int update(ChatRoom room) {

        String sql = """
                UPDATE chat_rooms
                SET room_name = ?,
                    description = ?
                WHERE room_id = ?
                """;

        return jdbcTemplate.update(
                sql,
                room.getRoomName(),
                room.getDescription(),
                room.getRoomId()
        );
    }

    // 5b. UPDATE ROOM OWNER
    public int updateOwner(Long roomId, Long ownerId) {

        String sql = """
                UPDATE chat_rooms
                SET owner_id = ?
                WHERE room_id = ?
                """;

        return jdbcTemplate.update(sql, ownerId, roomId);
    }

    // 6. DELETE ROOM
    public int deleteById(Long roomId) {

        String sql = """
                DELETE FROM chat_rooms
                WHERE room_id = ?
                """;

        return jdbcTemplate.update(sql, roomId);
    }

    // 7. FIND ROOMS OWNED BY A USER
    public List<ChatRoom> findByOwnerId(Long ownerId) {

        String sql = """
                SELECT * FROM chat_rooms
                WHERE owner_id = ?
                ORDER BY room_id
                """;

        return jdbcTemplate.query(
                sql,
                chatRoomRowMapper,
                ownerId
        );
    }

    // 8. CHECK WHETHER ROOM NAME EXISTS
    public boolean existsByRoomName(String roomName) {

        String sql = """
                SELECT COUNT(*)
                FROM chat_rooms
                WHERE room_name = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                roomName
        );

        return count != null && count > 0;
    }
}