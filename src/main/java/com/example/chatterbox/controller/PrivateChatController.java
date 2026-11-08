package com.example.chatterbox.controller;

import com.example.chatterbox.dto.MessageDto;
import com.example.chatterbox.dto.PrivateChatDto;
import com.example.chatterbox.dto.StartPrivateChatRequest;
import com.example.chatterbox.service.ChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST API for private chat management and PM message history.
 * All endpoints require an authenticated session.
 */
@RestController
@RequestMapping("/api/pm")
public class PrivateChatController {

    private final ChatService chatService;

    public PrivateChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ----------------------------------------------------------------
    // POST /api/pm/start — create or retrieve a private chat
    // ----------------------------------------------------------------

    @PostMapping("/start")
    public ResponseEntity<PrivateChatDto> startPrivateChat(
            @RequestBody StartPrivateChatRequest body,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        PrivateChatDto dto =
                chatService.startPrivateChat(
                        userId,
                        body.getTargetUsername()
                );

        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    // ----------------------------------------------------------------
    // GET /api/pm/list — list all PMs for the authenticated user
    // ----------------------------------------------------------------

    @GetMapping("/list")
    public ResponseEntity<List<PrivateChatDto>> listPrivateChats(
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        return ResponseEntity.ok(
                chatService.listPrivateChats(userId)
        );
    }

    // ----------------------------------------------------------------
    // GET /api/pm/{privateChatId}/history?limit=50
    // ----------------------------------------------------------------

    @GetMapping("/{privateChatId}/history")
    public ResponseEntity<List<MessageDto>> getHistory(
            @PathVariable Long privateChatId,
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        return ResponseEntity.ok(
                chatService.getPrivateChatHistory(
                        userId, privateChatId, limit
                )
        );
    }

    // ----------------------------------------------------------------
    // GET /api/pm/{privateChatId}/history/older?before=X&limit=20
    // ----------------------------------------------------------------

    @GetMapping("/{privateChatId}/history/older")
    public ResponseEntity<List<MessageDto>> getOlderMessages(
            @PathVariable Long privateChatId,
            @RequestParam Long before,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        return ResponseEntity.ok(
                chatService.getOlderPrivateMessages(
                        userId, privateChatId, before, limit
                )
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
