package com.example.chatterbox.model;

import java.time.LocalDateTime;

public class RoomMember {

    private Long roomId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;

    public RoomMember() {
    }

    public RoomMember(Long roomId, Long userId,
                      String role, LocalDateTime joinedAt) {

        this.roomId = roomId;
        this.userId = userId;
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