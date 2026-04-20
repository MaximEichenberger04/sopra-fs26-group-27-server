package ch.uzh.ifi.hase.soprafs26.websocket;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.service.GameDisconnectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final Map<Long, Map<Long, WebSocketSession>> gameSessions = new ConcurrentHashMap<>();
    private final Map<String, PlayerConnection> sessionIndex = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final GameDisconnectService gameDisconnectService;

    public GameWebSocketHandler(UserRepository userRepository,
                                GameDisconnectService gameDisconnectService) {
        this.userRepository = userRepository;
        this.gameDisconnectService = gameDisconnectService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (!payload.contains("\"type\":\"REGISTER\"")) {
            return;
        }

        Long gameId = extractLong(payload, "gameId");
        String token = extractString(payload, "token");

        if (gameId == null || token == null || token.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid register payload"));
            return;
        }

        User user = userRepository.findByToken(token);
        if (user == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            return;
        }

        gameSessions
            .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
            .put(user.getId(), session);

        sessionIndex.put(session.getId(), new PlayerConnection(gameId, user.getId()));

        gameDisconnectService.handleReconnect(gameId, user.getId());

        log.info("Registered ws session {} for game {} user {}", session.getId(), gameId, user.getId());
    }

    //helpers for handletextmessage
    private String extractString(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }

        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }

        return json.substring(start, end);
    }

    private Long extractLong(String json, String fieldName) {
        String numberPattern = "\"" + fieldName + "\":";
        int start = json.indexOf(numberPattern);
        if (start == -1) {
            return null;
        }

        start += numberPattern.length();

        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }

        if (start == end) {
            return null;
        }

        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        PlayerConnection connection = sessionIndex.remove(session.getId());
        if (connection != null) {
            Map<Long, WebSocketSession> perGame = gameSessions.get(connection.gameId());
            if (perGame != null) {
                perGame.remove(connection.userId());
                if (perGame.isEmpty()) {
                    gameSessions.remove(connection.gameId());
                }
            }

            gameDisconnectService.handleDisconnect(connection.gameId(), connection.userId());
        }

        log.info("WebSocket disconnected: {}", session.getId());
    }

    public boolean isPlayerConnected(Long gameId, Long userId) {
        Map<Long, WebSocketSession> perGame = gameSessions.get(gameId);
        if (perGame == null) {
            return false;
        }

        WebSocketSession session = perGame.get(userId);
        return session != null && session.isOpen();
    }


    public void broadcastGameEvent(String type, Long gameId) {
        broadcastGameEvent(type, gameId, null, null);
    }

    public void broadcastGameEvent(String type, Long gameId, Long userId, Integer gracePeriodSeconds) {
        StringBuilder payload = new StringBuilder();
        payload.append("{\"type\":\"").append(type).append("\",\"gameId\":\"").append(gameId).append("\"");

        if (userId != null) {
            payload.append(",\"userId\":\"").append(userId).append("\"");
        }
        if (gracePeriodSeconds != null) {
            payload.append(",\"gracePeriodSeconds\":").append(gracePeriodSeconds);
        }
        payload.append("}");

        TextMessage msg = new TextMessage(payload.toString());

        Map<Long, WebSocketSession> perGame = gameSessions.get(gameId);
        if (perGame == null) {
            return;
        }

        for (WebSocketSession s : perGame.values()) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException e) {
                    log.warn("send failed: {}", s.getId());
                }
            }
        }
    }

    private record PlayerConnection(Long gameId, Long userId) {}
}