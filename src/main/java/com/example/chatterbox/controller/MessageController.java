package com.example.chatterbox.controller;

import com.example.chatterbox.dto.MessageDto;
import com.example.chatterbox.service.ChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST API for editing and deleting individual messages.
 * Both operations require the authenticated user to be the sender.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final ChatService chatService;

    public MessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ----------------------------------------------------------------
    // PUT /api/messages/{messageId} — edit a message
    // ----------------------------------------------------------------

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable Long messageId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        String newContent = body.get("content");

        MessageDto updated =
                chatService.editMessage(userId, messageId, newContent);

        return ResponseEntity.ok(updated);
    }

    // ----------------------------------------------------------------
    // DELETE /api/messages/{messageId} — soft-delete a message
    // ----------------------------------------------------------------

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, String>> deleteMessage(
            @PathVariable Long messageId,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        chatService.deleteMessage(userId, messageId);

        return ResponseEntity.ok(
                Map.of("message", "Message deleted successfully")
        );
    }

    // ----------------------------------------------------------------
    // HELPER
    // ----------------------------------------------------------------

    private Long getSessionUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("userId") == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "You must be logged in"
            );
        }

        return (Long) session.getAttribute("userId");
    }
}
