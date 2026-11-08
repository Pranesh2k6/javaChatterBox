package com.example.chatterbox.controller;

import com.example.chatterbox.dto.MessageDto;
import com.example.chatterbox.dto.RoomDto;
import com.example.chatterbox.dto.RoomMemberDto;
import com.example.chatterbox.service.ChatService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST API for room management and room message history.
 *
 * All write operations require an authenticated session (userId
 * attribute present). Read endpoints (list, history) are open.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final ChatService chatService;

    public RoomController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ----------------------------------------------------------------
    // GET /api/rooms — list all rooms
    // ----------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<RoomDto>> listRooms() {

        return ResponseEntity.ok(chatService.getAllRooms());
    }

    // ----------------------------------------------------------------
    // POST /api/rooms — create a room (authenticated)
    // ----------------------------------------------------------------

    @PostMapping
    public ResponseEntity<RoomDto> createRoom(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        Long userId = getSessionUserId(request);

        String roomName = body.get("roomName");
        String description = body.getOrDefault("description", "");

        RoomDto created =
                chatService.createRoom(userId, roomName, description);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ----------------------------------------------------------------
    // GET /api/rooms/{roomId}/members — room member list
    // ----------------------------------------------------------------

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<RoomMemberDto>> getRoomMembers(
            @PathVariable Long roomId) {

        return ResponseEntity.ok(chatService.getRoomMembers(roomId));
    }

    // ----------------------------------------------------------------
    // GET /api/rooms/{roomId}/history?limit=50 — recent messages
    // ----------------------------------------------------------------

    @GetMapping("/{roomId}/history")
    public ResponseEntity<List<MessageDto>> getRoomHistory(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(
                chatService.getRoomHistory(roomId, limit)
        );
    }

    // ----------------------------------------------------------------
    // GET /api/rooms/{roomId}/history/older?before=X&limit=20
    // ----------------------------------------------------------------

    @GetMapping("/{roomId}/history/older")
    public ResponseEntity<List<MessageDto>> getOlderRoomMessages(
            @PathVariable Long roomId,
            @RequestParam Long before,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(
                chatService.getOlderRoomMessages(roomId, before, limit)
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
