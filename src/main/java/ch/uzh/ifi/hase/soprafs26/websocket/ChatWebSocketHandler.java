package ch.uzh.ifi.hase.soprafs26.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Handles the /chat_refresh_websocket endpoint.
 *
 * Sessions are grouped by gameId (parsed from query param ?gameId=<id>).
 * On connect, the full chat history for that game is replayed.
 * Incoming messages are persisted and broadcast to all sessions in the same room.
 *
 * Expected incoming JSON payload:
 *   {"userId": <long>, "username": "<str>", "text": "<str>", "gifUrl": "<str or null>"}
 *
 * Outgoing message JSON:
 *   {"id":<long>,"gameId":<long>,"userId":<long>,"username":"<str>","text":"<str>","gifUrl":"<str>","timestamp":<long>}
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    // gameId → set of connected sessions for that game room
    private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> gameSessions = new ConcurrentHashMap<>();
}
