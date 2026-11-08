package com.example.chatterbox.dto;

import java.time.LocalDateTime;

/**
 * DTO for returning user profile data in REST API responses.
 * Includes online status derived from ChatServer's client map.
 */
public class UserProfileDto {

    private Long userId;
    private String username;
    private LocalDateTime lastSeen;
    private boolean online;

    public UserProfileDto() {
    }

    public UserProfileDto(Long userId,
                          String username,
                          LocalDateTime lastSeen,
                          boolean online) {

        this.userId = userId;
        this.username = username;
        this.lastSeen = lastSeen;
        this.online = online;
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

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
