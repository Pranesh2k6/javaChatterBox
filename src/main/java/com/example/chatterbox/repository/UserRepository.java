package com.example.chatterbox.repository;

import com.example.chatterbox.model.*;

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
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert database row into User object
    private final RowMapper<User> userRowMapper =
            new RowMapper<User>() {

        @Override
        public User mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            Timestamp created = rs.getTimestamp("created_at");
            Timestamp lastSeen = rs.getTimestamp("last_seen");

            return new User(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    created != null ? created.toLocalDateTime() : null,
                    lastSeen != null ? lastSeen.toLocalDateTime() : null,
                    rs.getBoolean("is_active")
            );
        }
    };

    // 1. INSERT USER
    public Long save(User user) {

        String sql = """
                INSERT INTO users (username, password_hash)
                VALUES (?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement ps = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS
            );

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());

            return ps;

        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    // 2. FIND USER BY ID
    public Optional<User> findById(Long userId) {

        String sql =
                "SELECT * FROM users WHERE user_id = ?";

        List<User> users = jdbcTemplate.query(
                sql,
                userRowMapper,
                userId
        );

        return users.stream().findFirst();
    }

    // 3. FIND USER BY USERNAME
    public Optional<User> findByUsername(String username) {

        String sql =
                "SELECT * FROM users WHERE username = ?";

        List<User> users = jdbcTemplate.query(
                sql,
                userRowMapper,
                username
        );

        return users.stream().findFirst();
    }

    // 4. GET ALL USERS
    public List<User> findAll() {

        String sql =
                "SELECT * FROM users ORDER BY user_id";

        return jdbcTemplate.query(sql, userRowMapper);
    }

    // 5. UPDATE USER
    public int update(User user) {

        String sql = """
                UPDATE users
                SET username = ?,
                    password_hash = ?,
                    is_active = ?
                WHERE user_id = ?
                """;

        return jdbcTemplate.update(
                sql,
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),
                user.getUserId()
        );
    }

    // 6. UPDATE LAST SEEN
    public int updateLastSeen(Long userId) {

        String sql = """
                UPDATE users
                SET last_seen = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;

        return jdbcTemplate.update(sql, userId);
    }

    // 7. DELETE USER
    public int deleteById(Long userId) {

        String sql =
                "DELETE FROM users WHERE user_id = ?";

        return jdbcTemplate.update(sql, userId);
    }

    // 8. SEARCH USERS BY USERNAME PREFIX
    public List<User> searchByUsername(String query) {

        String sql =
                "SELECT * FROM users WHERE username LIKE ? LIMIT 20";

        return jdbcTemplate.query(
                sql,
                userRowMapper,
                query + "%"
        );
    }

    // 9. CHECK USERNAME EXISTS
    public boolean existsByUsername(String username) {

        String sql = """
                SELECT COUNT(*)
                FROM users
                WHERE username = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                username
        );

        return count != null && count > 0;
    }
}