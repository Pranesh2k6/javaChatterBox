package com.example.chatterbox.model;

import java.time.LocalDateTime;

public class ChatRoom {

    private Long roomId;
    private String roomName;
    private String description;
    private Long ownerId;
    private LocalDateTime createdAt;

    public ChatRoom() {
    }

    public ChatRoom(Long roomId, String roomName,
                    String description, Long ownerId,
                    LocalDateTime createdAt) {

        this.roomId = roomId;
        this.roomName = roomName;
        this.description = description;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}