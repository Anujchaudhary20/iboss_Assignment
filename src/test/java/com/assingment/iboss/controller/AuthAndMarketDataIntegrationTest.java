package com.assingment.iboss.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.assingment.iboss.IbossApplication;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IbossApplication.class)
@AutoConfigureMockMvc
class AuthAndMarketDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String obtainToken(String username, String password) throws Exception {
        String loginJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("token").asText();
    }

    @Test
    @DisplayName("Should successfully authenticate and return JWT token for valid credentials")
    void testLoginSuccess() throws Exception {
        String loginJson = "{\"username\":\"trader1\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.username", is("trader1")))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_USER")));
    }

    @Test
    @DisplayName("Should reject authentication for invalid credentials")
    void testLoginFailure() throws Exception {
        String loginJson = "{\"username\":\"trader1\",\"password\":\"bad_password\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject unauthenticated access to /api/market/tickers")
    void testProtectedTickersWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/market/tickers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow authenticated access to /api/market/tickers and return OKX format")
    void testProtectedTickersWithAuth() throws Exception {
        String token = obtainToken("trader1", "password123");

        mockMvc.perform(get("/api/market/tickers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("0")))
                .andExpect(jsonPath("$.data", hasSize(20)))
                .andExpect(jsonPath("$.data[0].instId", notNullValue()))
                .andExpect(jsonPath("$.data[0].last", notNullValue()))
                .andExpect(jsonPath("$.data[0].volCcy24h", notNullValue()));
    }

    @Test
    @DisplayName("Should reject regular USER from accessing /api/admin/sessions with 403 Forbidden")
    void testAdminEndpointForbiddenForUser() throws Exception {
        String traderToken = obtainToken("trader1", "password123");

        mockMvc.perform(get("/api/admin/sessions")
                        .header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow ADMIN role to access /api/admin/sessions")
    void testAdminEndpointAllowedForAdmin() throws Exception {
        String adminToken = obtainToken("admin", "admin123");

        mockMvc.perform(get("/api/admin/sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessionCount", notNullValue()))
                .andExpect(jsonPath("$.totalConnections", notNullValue()))
                .andExpect(jsonPath("$.rejectedDuplicateConnections", notNullValue()));
    }
}
