
package com.example.chatterbox.service;

import com.example.chatterbox.dto.AuthResponse;
import com.example.chatterbox.dto.RegisterRequest;
import com.example.chatterbox.dto.LoginRequest;
import com.example.chatterbox.model.User;
import com.example.chatterbox.repository.UserRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }


    // USER REGISTRATION
    public AuthResponse register(RegisterRequest request) {

        if (request == null ||
                request.getUsername() == null ||
                request.getPassword() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username and password are required"
            );
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (!username.matches("[A-Za-z0-9_]{3,20}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username must be 3–20 characters: letters, numbers, or underscores only"
            );
        }

        if (password.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password cannot be empty"
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too long"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        String hashedPassword =
                passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashedPassword);
        user.setActive(true);

        try {

            Long userId = userRepository.save(user);

            User savedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException(
                            "User could not be retrieved"
                    ));

            return new AuthResponse(
                    "Registration successful",
                    savedUser.getUserId(),
                    savedUser.getUsername()
            );

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }
    }

    // USER LOGIN
    public AuthResponse login(LoginRequest request, HttpSession session) {

        // 1. Validate request
        if (request == null ||
                request.getUsername() == null ||
                request.getPassword() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Username and password are required"
            );
        }

        String username = request.getUsername().trim();
        String password = request.getPassword();

        // 2. Find user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"
                ));

        // 3. Check account status
        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive"
            );
        }

        // 4. Verify BCrypt password
        if (!passwordEncoder.matches(
                password,
                user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        // 5. Store authenticated user information
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("username", user.getUsername());

        // 6. Return successful response
        return new AuthResponse(
                "Login successful",
                user.getUserId(),
                user.getUsername()
        );
    }
}
