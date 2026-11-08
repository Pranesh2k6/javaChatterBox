package com.example.chatterbox.repository;

import com.example.chatterbox.model.RoomMember;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class RoomMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert database row into RoomMember object
    private final RowMapper<RoomMember> roomMemberRowMapper =
            new RowMapper<RoomMember>() {

        @Override
        public RoomMember mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            Timestamp joined = rs.getTimestamp("joined_at");

            return new RoomMember(
                    rs.getLong("room_id"),
                    rs.getLong("user_id"),
                    rs.getString("role"),
                    joined != null ? joined.toLocalDateTime() : null
            );
        }
    };

    // 1. ADD USER TO ROOM
    public int save(RoomMember member) {

        String sql = """
                INSERT INTO room_members
                (room_id, user_id, role)
                VALUES (?, ?, ?)
                """;

        return jdbcTemplate.update(
                sql,
                member.getRoomId(),
                member.getUserId(),
                member.getRole()
        );
    }

    // 2. FIND MEMBERSHIP BY ROOM ID AND USER ID
    public Optional<RoomMember> findByRoomIdAndUserId(
            Long roomId, Long userId) {

        String sql = """
                SELECT * FROM room_members
                WHERE room_id = ? AND user_id = ?
                """;

        List<RoomMember> members = jdbcTemplate.query(
                sql,
                roomMemberRowMapper,
                roomId,
                userId
        );

        return members.stream().findFirst();
    }

    // 3. GET ALL MEMBERS OF A ROOM
    public List<RoomMember> findByRoomId(Long roomId) {

        String sql = """
                SELECT * FROM room_members
                WHERE room_id = ?
                ORDER BY joined_at
                """;

        return jdbcTemplate.query(
                sql,
                roomMemberRowMapper,
                roomId
        );
    }

    // 4. GET ALL ROOMS JOINED BY A USER
    public List<RoomMember> findByUserId(Long userId) {

        String sql = """
                SELECT * FROM room_members
                WHERE user_id = ?
                ORDER BY joined_at
                """;

        return jdbcTemplate.query(
                sql,
                roomMemberRowMapper,
                userId
        );
    }

    // 5. UPDATE MEMBER ROLE
    public int updateRole(
            Long roomId, Long userId, String role) {

        String sql = """
                UPDATE room_members
                SET role = ?
                WHERE room_id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql,
                role,
                roomId,
                userId
        );
    }

    // 6. REMOVE USER FROM ROOM
    public int delete(Long roomId, Long userId) {

        String sql = """
                DELETE FROM room_members
                WHERE room_id = ? AND user_id = ?
                """;

        return jdbcTemplate.update(
                sql,
                roomId,
                userId
        );
    }

    // 7. CHECK WHETHER USER IS A ROOM MEMBER
    public boolean exists(Long roomId, Long userId) {

        String sql = """
                SELECT COUNT(*)
                FROM room_members
                WHERE room_id = ? AND user_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                roomId,
                userId
        );

        return count != null && count > 0;
    }

    // 8. COUNT ROOM MEMBERS
    public int countMembers(Long roomId) {

        String sql = """
                SELECT COUNT(*)
                FROM room_members
                WHERE room_id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                roomId
        );

        return count != null ? count : 0;
    }
}