package com.assingment.iboss.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSessionManagerTest {

    private ClientSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new ClientSessionManager();
    }

    @Test
    @DisplayName("Should successfully register a new client session")
    void testSingleSessionRegistration() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-001");
        when(session.isOpen()).thenReturn(true);
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 54321));

        boolean registered = sessionManager.registerSession("trader1", session);

        assertTrue(registered, "First session registration should succeed");
        assertTrue(sessionManager.hasActiveSession("trader1"));
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertEquals("trader1", sessionManager.getClientId("sess-001"));
        assertEquals(session, sessionManager.getSession("trader1"));
        assertEquals(1, sessionManager.getTotalConnections());
        assertEquals(0, sessionManager.getRejectedConnections());
    }

    @Test
    @DisplayName("STRICT REJECTION: Should strictly reject duplicate connection attempt for active client")
    void testStrictRejectionOfDuplicateSession() {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-001");
        when(session1.isOpen()).thenReturn(true);

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("sess-002");
        when(session2.isOpen()).thenReturn(true);

        // Register first session
        boolean firstReg = sessionManager.registerSession("trader1", session1);
        assertTrue(firstReg);

        // Attempt duplicate connection with same clientId
        boolean duplicateReg = sessionManager.registerSession("trader1", session2);

        assertFalse(duplicateReg, "Duplicate connection attempt must be strictly rejected");
        assertEquals(1, sessionManager.getActiveSessionCount(), "Active session count must remain 1");
        assertEquals(session1, sessionManager.getSession("trader1"), "Initial session must remain active");
        assertEquals(2, sessionManager.getTotalConnections());
        assertEquals(1, sessionManager.getRejectedConnections(), "Rejected connection counter must increment");
    }

    @Test
    @DisplayName("Should allow new registration after previous session cleanly unregisters")
    void testSessionUnregistrationAndReconnection() {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("sess-001");
        when(session1.isOpen()).thenReturn(true);

        sessionManager.registerSession("trader1", session1);
        assertTrue(sessionManager.hasActiveSession("trader1"));

        // Unregister session1
        sessionManager.unregisterSession("sess-001");
        assertFalse(sessionManager.hasActiveSession("trader1"));
        assertEquals(0, sessionManager.getActiveSessionCount());

        // Connect again
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("sess-002");
        when(session2.isOpen()).thenReturn(true);

        boolean secondReg = sessionManager.registerSession("trader1", session2);
        assertTrue(secondReg, "Reconnecting after previous session ended must succeed");
        assertEquals(1, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Should administratively terminate an active session")
    void testAdministrativeTermination() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-001");
        when(session.isOpen()).thenReturn(true);

        sessionManager.registerSession("trader1", session);
        assertTrue(sessionManager.hasActiveSession("trader1"));

        boolean terminated = sessionManager.terminateSession("trader1");

        assertTrue(terminated);
        verify(session, times(1)).close(any());
        assertFalse(sessionManager.hasActiveSession("trader1"));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }
}
