package com.finguard.controller;

import com.finguard.dto.ChangePasswordRequest;
import com.finguard.dto.UserInfo;
import com.finguard.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * All endpoints here are protected by the API Gateway.
 * The gateway validates the JWT and injects:
 *   X-User-Id   — the authenticated user's UUID
 *   X-User-Role — USER or ADMIN
 *
 * No JWT validation happens in this service.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getProfile(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userService.getProfile(UUID.fromString(userId)));
    }

    @PatchMapping("/me/name")
    public ResponseEntity<UserInfo> updateName(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String name) {
        return ResponseEntity.ok(userService.updateFullName(UUID.fromString(userId), name));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(UUID.fromString(userId), req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<UserInfo>> getAllUsers(
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/admin/{targetId}/enabled")
    public ResponseEntity<Void> setEnabled(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID targetId,
            @RequestParam boolean enabled) {
        if (!"ADMIN".equals(role)) return ResponseEntity.status(403).build();
        userService.setEnabled(targetId, enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/{targetId}/promote")
    public ResponseEntity<Void> promote(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID targetId) {
        if (!"ADMIN".equals(role)) return ResponseEntity.status(403).build();
        userService.promoteToAdmin(targetId);
        return ResponseEntity.noContent().build();
    }
}
