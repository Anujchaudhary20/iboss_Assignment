package com.assingment.iboss.controller;

import com.assingment.iboss.service.ClientSessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Endpoints for managing client sessions and inspecting metrics")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final ClientSessionManager clientSessionManager;

    @Operation(summary = "Get active client sessions and connection statistics",
            description = "Requires ROLE_ADMIN. Returns active session count, total connections, rejected duplicates, and active session details.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions() {
        List<ClientSessionManager.SessionMetadata> activeSessions = clientSessionManager.getActiveSessionList();
        return ResponseEntity.ok(Map.of(
                "activeSessionCount", clientSessionManager.getActiveSessionCount(),
                "totalConnections", clientSessionManager.getTotalConnections(),
                "rejectedDuplicateConnections", clientSessionManager.getRejectedConnections(),
                "sessions", activeSessions
        ));
    }

    @Operation(summary = "Administratively terminate an active client session",
            description = "Requires ROLE_ADMIN. Disconnects the active session for the given clientId.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/sessions/{clientId}/terminate")
    public ResponseEntity<Map<String, Object>> terminateSession(@PathVariable String clientId) {
        boolean terminated = clientSessionManager.terminateSession(clientId);
        if (terminated) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Session terminated successfully for clientId: " + clientId
            ));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", "No active session found for clientId: " + clientId
            ));
        }
    }
}
