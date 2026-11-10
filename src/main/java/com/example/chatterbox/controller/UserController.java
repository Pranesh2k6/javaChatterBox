package com.example.chatterbox.controller;

import com.example.chatterbox.dto.UserProfileDto;
import com.example.chatterbox.network.ChatServer;
import com.example.chatterbox.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * REST API for user search, online status, and profile retrieval.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ChatServer chatServer;

    public UserController(UserService userService,
                          ChatServer chatServer) {

        this.userService = userService;
        this.chatServer = chatServer;
    }

    // ----------------------------------------------------------------
    // GET /api/users — list all active users
    // ----------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<UserProfileDto>> listAllUsers(
            HttpServletRequest request) {

        requireAuthentication(request);

        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ----------------------------------------------------------------
    // GET /api/users/search?q=<prefix> — search by username prefix
    // ----------------------------------------------------------------

    @GetMapping("/search")
    public ResponseEntity<List<UserProfileDto>> searchUsers(
            @RequestParam("q") String query,
            HttpServletRequest request) {

        requireAuthentication(request);

        return ResponseEntity.ok(
                userService.searchByUsername(query)
        );
    }

    // ----------------------------------------------------------------
    // GET /api/users/online — list all currently online usernames
    // ----------------------------------------------------------------

    @GetMapping("/online")
    public ResponseEntity<Set<String>> getOnlineUsers() {

        return ResponseEntity.ok(chatServer.getOnlineUsernames());
    }

    // ----------------------------------------------------------------
    // GET /api/users/{userId} — get a user's public profile
    // ----------------------------------------------------------------

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileDto> getUserProfile(
            @PathVariable Long userId,
            HttpServletRequest request) {

        requireAuthentication(request);

        return ResponseEntity.ok(
                userService.getUserProfile(userId)
        );
    }

    // ----------------------------------------------------------------
    // HELPER
    // ----------------------------------------------------------------

    private void requireAuthentication(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("userId") == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "You must be logged in"
            );
        }
    }
}
