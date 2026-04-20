package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store for chat history.
 *
 * Per-game chat is keyed by gameId and initialised/evicted 
 * the game lifecycle (see GameService). Global chat (not yet implemented)
 * will live here as a separate flat list.
 */
@Component
public class ChatCache {

    private final Map<Long, List<ChatMessage>> gameChatMessages = new ConcurrentHashMap<>();

    // Initialises an empty chat history for the given game.
    public void initGame(Long gameId) {
        gameChatMessages.put(gameId, new CopyOnWriteArrayList<>());
    }

    // Removes all chat history for a finished game to free memory.
    public void evictGame(Long gameId) {
        gameChatMessages.remove(gameId);
    }

    // Appends a message to the chat history for the given game.
    public void addGameMessage(Long gameId, ChatMessage message) {
        List<ChatMessage> history = gameChatMessages.get(gameId);
        if (history == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat history not found for game " + gameId);
        }
        history.add(message);
    }

    // Returns the full chat history for the given game, or an empty list if not found.
    public List<ChatMessage> getGameHistory(Long gameId) {
        return gameChatMessages.getOrDefault(gameId, List.of());
    }
}
