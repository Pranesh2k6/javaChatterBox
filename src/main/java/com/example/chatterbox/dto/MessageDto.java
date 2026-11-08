package com.example.chatterbox.dto;

import java.time.LocalDateTime;

/**
 * DTO for returning message data in REST API responses.
 * Includes enriched sender username resolved from the sender ID.
 */
public class MessageDto {

    private Long messageId;
    private Long senderId;
    private String senderUsername;
    private Long roomId;
    private Long privateChatId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public MessageDto() {
    }

    public MessageDto(Long messageId,
                      Long senderId,
                      String senderUsername,
                      Long roomId,
                      Long privateChatId,
                      String content,
                      LocalDateTime createdAt,
                      LocalDateTime updatedAt,
                      LocalDateTime deletedAt) {

        this.messageId = messageId;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.roomId = roomId;
        this.privateChatId = privateChatId;
        this.content = content;
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

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
