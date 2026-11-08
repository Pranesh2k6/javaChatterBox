package com.example.chatterbox.dto;

import java.time.LocalDateTime;

/**
 * DTO for returning private chat data in REST API responses.
 * Includes otherUsername - the other participant's name relative
 * to the requesting user.
 */
public class PrivateChatDto {

    private Long privateChatId;
    private Long user1Id;
    private Long user2Id;
    private String otherUsername;
    private LocalDateTime createdAt;

    public PrivateChatDto() {
    }

    public PrivateChatDto(Long privateChatId,
                          Long user1Id,
                          Long user2Id,
                          String otherUsername,
                          LocalDateTime createdAt) {

        this.privateChatId = privateChatId;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.otherUsername = otherUsername;
        this.createdAt = createdAt;
    }

    public Long getPrivateChatId() {
        return privateChatId;
    }

    public void setPrivateChatId(Long privateChatId) {
        this.privateChatId = privateChatId;
    }

    public Long getUser1Id() {
        return user1Id;
    }

    public void setUser1Id(Long user1Id) {
        this.user1Id = user1Id;
    }

    public Long getUser2Id() {
        return user2Id;
    }

    public void setUser2Id(Long user2Id) {
        this.user2Id = user2Id;
    }

    public String getOtherUsername() {
        return otherUsername;
    }

    public void setOtherUsername(String otherUsername) {
        this.otherUsername = otherUsername;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
