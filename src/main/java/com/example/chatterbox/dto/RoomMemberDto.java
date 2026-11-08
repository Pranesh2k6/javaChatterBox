package com.example.chatterbox.dto;

import java.time.LocalDateTime;

/**
 * DTO for returning room member data in REST API responses.
 * Includes enriched username resolved from the user ID.
 */
public class RoomMemberDto {

    private Long roomId;
    private Long userId;
    private String username;
    private String role;
    private LocalDateTime joinedAt;

    public RoomMemberDto() {
    }

    public RoomMemberDto(Long roomId,
                         Long userId,
                         String username,
                         String role,
                         LocalDateTime joinedAt) {

        this.roomId = roomId;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
