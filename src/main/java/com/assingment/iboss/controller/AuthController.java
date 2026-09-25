package com.assingment.iboss.controller;

import com.assingment.iboss.model.auth.LoginRequest;
import com.assingment.iboss.model.auth.LoginResponse;
import com.assingment.iboss.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and token issuance")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Operation(summary = "Authenticate user and issue JWT token",
            description = "Default accounts: trader1/password123 (ROLE_USER), trader2/password123 (ROLE_USER), admin/admin123 (ROLE_ADMIN)")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String token = jwtService.generateToken(authentication.getName(), authentication.getAuthorities());

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(authentication.getName())
                .roles(roles)
                .expiresInMs(jwtService.getExpirationMs())
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get current authenticated user profile")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "roles", roles
        ));
    }
}
