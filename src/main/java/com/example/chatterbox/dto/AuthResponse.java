
package com.example.chatterbox.dto;

public class AuthResponse {

    private String message;
    private Long userId;
    private String username;

    public AuthResponse() {
    }

    public AuthResponse(String message, Long userId, String username) {
        this.message = message;
        this.userId = userId;
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}
