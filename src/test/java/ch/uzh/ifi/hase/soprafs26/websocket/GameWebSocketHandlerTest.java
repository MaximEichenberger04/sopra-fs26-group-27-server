package ch.uzh.ifi.hase.soprafs26.websocket;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.GameDisconnectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GameWebSocketHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private GameDisconnectService gameDisconnectService;
    @Mock private WebSocketSession session;

    @InjectMocks
    private GameWebSocketHandler handler;

    private User user;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        user = new User();
        user.setId(42L);
        user.setToken("good-token");
        when(userRepository.findByToken("good-token")).thenReturn(user);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
    }

    // ════════════════════════════════════════════════
    // handleTextMessage / register protocol
    // ════════════════════════════════════════════════
    @Test
    public void handleTextMessage_validRegister_registersSessionAndCallsReconnect() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7,\"token\":\"good-token\"}";

        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        verify(gameDisconnectService).handleReconnect(7L, 42L);
        assertTrue(handler.isPlayerConnected(7L, 42L));
    }

    @Test
    public void handleTextMessage_notRegisterType_ignored() throws Exception {
        String payload = "{\"type\":\"OTHER\",\"gameId\":7}";

        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        verify(gameDisconnectService, never()).handleReconnect(anyLong(), anyLong());
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    public void handleTextMessage_missingGameId_closesSession() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"token\":\"good-token\"}";

        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        verify(session).close(any(CloseStatus.class));
        verify(gameDisconnectService, never()).handleReconnect(anyLong(), anyLong());
    }

    @Test
    public void handleTextMessage_missingToken_closesSession() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7}";

        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        verify(session).close(any(CloseStatus.class));
        verify(gameDisconnectService, never()).handleReconnect(anyLong(), anyLong());
    }

    @Test
    public void handleTextMessage_invalidToken_closesSession() throws Exception {
        when(userRepository.findByToken("bad-token")).thenReturn(null);
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7,\"token\":\"bad-token\"}";

        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        verify(session).close(any(CloseStatus.class));
        verify(gameDisconnectService, never()).handleReconnect(anyLong(), anyLong());
    }

    // ════════════════════════════════════════════════
    // afterConnectionClosed
    // ════════════════════════════════════════════════
    @Test
    public void afterConnectionClosed_knownSession_removesAndTriggersDisconnect() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7,\"token\":\"good-token\"}";
        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(gameDisconnectService).handleDisconnect(7L, 42L);
        assertFalse(handler.isPlayerConnected(7L, 42L));
    }

    @Test
    public void afterConnectionClosed_unknownSession_noOp() {
        when(session.getId()).thenReturn("never-registered");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(gameDisconnectService, never()).handleDisconnect(anyLong(), anyLong());
    }

    // ════════════════════════════════════════════════
    // isPlayerConnected
    // ════════════════════════════════════════════════
    @Test
    public void isPlayerConnected_noGame_returnsFalse() {
        assertFalse(handler.isPlayerConnected(999L, 1L));
    }

    @Test
    public void isPlayerConnected_sessionClosed_returnsFalse() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7,\"token\":\"good-token\"}";
        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        when(session.isOpen()).thenReturn(false);
        assertFalse(handler.isPlayerConnected(7L, 42L));
    }

    @Test
    public void isPlayerConnected_openSession_returnsTrue() throws Exception {
        String payload = "{\"type\":\"REGISTER\",\"gameId\":7,\"token\":\"good-token\"}";
        invokeHandleTextMessage(handler, session, new TextMessage(payload));

        assertTrue(handler.isPlayerConnected(7L, 42L));
    }

    // ════════════════════════════════════════════════
    // broadcastGameEvent
    // ════════════════════════════════════════════════
    @Test
    public void broadcastGameEvent_basic_sendsTypeAndGameId() throws Exception {
        registerSession(7L, 42L, session);

        handler.broadcastGameEvent("MOVE", 7L);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String json = captor.getValue().getPayload();
        assertTrue(json.contains("\"type\":\"MOVE\""));
        assertTrue(json.contains("\"gameId\":\"7\""));
    }

    @Test
    public void broadcastGameEvent_withCoords_includesRowAndCol() throws Exception {
        registerSession(7L, 42L, session);

        handler.broadcastGameEvent("FIREBALL", 7L, 3, 4);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String json = captor.getValue().getPayload();
        assertTrue(json.contains("\"type\":\"FIREBALL\""));
        assertTrue(json.contains("\"targetRow\":3"));
        assertTrue(json.contains("\"targetCol\":4"));
    }

    @Test
    public void broadcastGameEvent_withUserIdAndGrace_includesFields() throws Exception {
        registerSession(7L, 42L, session);

        handler.broadcastGameEvent("PLAYER_DISCONNECTED", 7L, 42L, 30);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String json = captor.getValue().getPayload();
        assertTrue(json.contains("\"type\":\"PLAYER_DISCONNECTED\""));
        assertTrue(json.contains("\"userId\":\"42\""));
        assertTrue(json.contains("\"gracePeriodSeconds\":30"));
    }

    @Test
    public void broadcastGameEvent_nullUserIdAndGrace_omitsFields() throws Exception {
        registerSession(7L, 42L, session);

        handler.broadcastGameEvent("GAME_UPDATED", 7L, null, null);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String json = captor.getValue().getPayload();
        assertFalse(json.contains("userId"));
        assertFalse(json.contains("gracePeriodSeconds"));
    }

    @Test
    public void broadcastGameEvent_noSessionsForGame_noOp() {
        handler.broadcastGameEvent("MOVE", 999L);
        // Should simply not throw
    }

    @Test
    public void broadcastGameEvent_closedSessionsSkipped() throws Exception {
        registerSession(7L, 42L, session);
        when(session.isOpen()).thenReturn(false);

        handler.broadcastGameEvent("MOVE", 7L);

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    // ---------- helpers ----------
    private void registerSession(Long gameId, Long userId, WebSocketSession s) {
        @SuppressWarnings("unchecked")
        Map<Long, Map<Long, WebSocketSession>> gameSessions =
                (Map<Long, Map<Long, WebSocketSession>>) ReflectionTestUtils.getField(handler, "gameSessions");
        gameSessions.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>()).put(userId, s);
    }

    private void invokeHandleTextMessage(GameWebSocketHandler h, WebSocketSession s, TextMessage m) throws Exception {
        Method method = GameWebSocketHandler.class.getDeclaredMethod(
                "handleTextMessage", WebSocketSession.class, TextMessage.class);
        method.setAccessible(true);
        method.invoke(h, s, m);
    }
}
