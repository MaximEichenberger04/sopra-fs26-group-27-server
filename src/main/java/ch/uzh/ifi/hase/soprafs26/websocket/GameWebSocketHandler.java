package ch.uzh.ifi.hase.soprafs26.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import java.io.IOException;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles the /game-refresh-websocket endpoint.
 *
 * Maintains a flat list of all connected sessions (across all games).
 * On any game state change, broadcasts a typed event so the client knows
 * what happened and can decide whether to re-fetch.
 *
 * Message format sent to clients:
 *   {"type":"MOVE","gameId":"<id>"}
 *   {"type":"WALL","gameId":"<id>"}
 *   {"type":"FORFEIT","gameId":"<id>"}
 *   {"type":"GAME_STARTED","gameId":"<id>"}
 *   {"type":"GAME_OVER","gameId":"<id>"}
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    /**
     * Broadcasts a typed game event to all connected clients.
     *
     * Callers:
     *   broadcastGameEvent("GAME_STARTED", gameId), called by LobbyService.startLobby
     *   broadcastGameEvent("MOVE",         gameId), called by MoveService.processMove
     *   broadcastGameEvent("WALL",         gameId), called by MoveService.applyWallPlacement
     *   broadcastGameEvent("FORFEIT",      gameId), called by GameService.forfeitGame
     *   broadcastGameEvent("GAME_OVER",    gameId), called by GameService.forfeitGame / MoveService.processMove (win)
     *
     * @param type   one of: GAME_STARTED, MOVE, WALL, FORFEIT, GAME_OVER
     * @param gameId the affected game
     */
    public void broadcastGameEvent(String type, Long gameId) {
        String payload = String.format("{\"type\":\"%s\",\"gameId\":\"%d\"}", type, gameId);
        TextMessage msg = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try { s.sendMessage(msg); }
                catch (IOException e) { log.warn("send failed: {}", s.getId()); }
            }
        }
    }
}
