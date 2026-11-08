package com.example.chatterbox.dto;

/**
 * Request body for starting a private chat session.
 * The client supplies the target user's username.
 */
public class StartPrivateChatRequest {

    private String targetUsername;

    public StartPrivateChatRequest() {
    }

    public StartPrivateChatRequest(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }
}
