package com.example.chatterbox.model;

import java.time.LocalDateTime;

public class PrivateChat {

    private Long privateChatId;
    private Long user1Id;
    private Long user2Id;
    private LocalDateTime createdAt;

    public PrivateChat() {
    }

    public PrivateChat(Long privateChatId,
                       Long user1Id,
                       Long user2Id,
                       LocalDateTime createdAt) {

        this.privateChatId = privateChatId;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}