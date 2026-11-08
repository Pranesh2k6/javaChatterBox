package com.example.chatterbox.model;

import java.time.LocalDateTime;

public class Message {

    private Long messageId;
    private Long senderId;
    private Long roomId;
    private Long privateChatId;
    private String messageContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Message() {
    }

    public Message(Long messageId, Long senderId,
                   Long roomId, Long privateChatId,
                   String messageContent,
                   LocalDateTime createdAt,
                   LocalDateTime updatedAt,
                   LocalDateTime deletedAt) {

        this.messageId = messageId;
        this.senderId = senderId;
        this.roomId = roomId;
        this.privateChatId = privateChatId;
        this.messageContent = messageContent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Long getPrivateChatId() {
        return privateChatId;
    }

    public void setPrivateChatId(Long privateChatId) {
        this.privateChatId = privateChatId;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}