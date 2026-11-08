
package com.example.chatterbox.controller;

import com.example.chatterbox.dto.AuthResponse;
import com.example.chatterbox.dto.RegisterRequest;
import com.example.chatterbox.dto.LoginRequest;
import com.example.chatterbox.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // USER REGISTRATION
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // USER LOGIN
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        // Create a session if one does not exist
        HttpSession session = request.getSession(true);

        // Authenticate user and store session attributes
        AuthResponse response =
                authService.login(loginRequest, session);

        // Rotate session ID after successful authentication
        request.changeSessionId();

        return ResponseEntity.ok(response);
    }


    // USER LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                Map.of("message", "Logout successful")
        );
    }

    // 4. CHECK AUTHENTICATED USER
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("userId") == null ||
                session.getAttribute("username") == null) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "You are not logged in"
            );
        }

        Long userId = (Long) session.getAttribute("userId");

        String username =
                (String) session.getAttribute("username");

        AuthResponse response = new AuthResponse(
                "Authenticated",
                userId,
                username
        );

        return ResponseEntity.ok(response);
    }
}
