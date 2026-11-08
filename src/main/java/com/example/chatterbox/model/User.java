package com.example.chatterbox.model;

import java.time.LocalDateTime;

public class User {
    private Long userId;
    private String username;
    private String passwordHash;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeen;
    private boolean active;

    public User() {
    }

    public User(Long userId, String username,
                String passwordHash,
                LocalDateTime createdAt,
                LocalDateTime lastSeen,
                boolean active) {

        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.lastSeen = lastSeen;
        this.active = active;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
