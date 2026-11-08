package com.example.chatterbox.service;

import com.example.chatterbox.dto.UserProfileDto;
import com.example.chatterbox.model.User;
import com.example.chatterbox.network.ChatServer;
import com.example.chatterbox.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for user-related REST operations.
 * Provides user search and profile retrieval, combining DB data
 * with live online-status from ChatServer.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ChatServer chatServer;

    public UserService(UserRepository userRepository,
                       ChatServer chatServer) {

        this.userRepository = userRepository;
        this.chatServer = chatServer;
    }

    /**
     * Search for users whose username starts with the given prefix.
     * Returns up to 20 matches with live online status.
     */
    public List<UserProfileDto> searchByUsername(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<User> users =
                userRepository.searchByUsername(query.trim());

        List<UserProfileDto> results = new ArrayList<>();

        for (User user : users) {

            boolean online = chatServer.isOnline(user.getUsername());

            results.add(new UserProfileDto(
                    user.getUserId(),
                    user.getUsername(),
                    user.getLastSeen(),
                    online
            ));
        }

        return results;
    }

    /**
     * Return the full profile for a specific user ID.
     * Throws 404 if the user does not exist.
     */
    public UserProfileDto getUserProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        boolean online = chatServer.isOnline(user.getUsername());

        return new UserProfileDto(
                user.getUserId(),
                user.getUsername(),
                user.getLastSeen(),
                online
        );
    }
}
