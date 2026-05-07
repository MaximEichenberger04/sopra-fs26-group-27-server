package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessagePostDTO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Business logic for in-game chat.
 *
 * Validates incoming messages, assigns IDs and timestamps, stores them in
 * ChatCache, and provides history retrieval.
 *
 * Chat is in-memory only. History is discarded when evictGame is called on game end.
 */
@Service
public class ChatService {

    private static final int MAX_TEXT_LENGTH = 500;
    private static final String KLIPY_PREFIX = "https://static.klipy.com/";

    // Thread-safe counter: multiple WebSocket threads (one websocket connection per player) may call sendMessage at the same time.
    private final AtomicLong messageIdCounter = new AtomicLong(0);

    private final ChatCache chatCache;
    private final GameRepository gameRepository;

    public ChatService(ChatCache chatCache, GameRepository gameRepository) {
        this.chatCache = chatCache;
        this.gameRepository = gameRepository;
    }

    /**
     * Validates and stores a chat message for the given game.
     * Returns the stored message as a DTO (with assigned id and timestamp).
     */
    public ChatMessageGetDTO sendMessage(Long gameId, ChatMessagePostDTO dto) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found: " + gameId));

        if (game.getGameStatus() != GameStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game " + gameId + " is not running");
        }

        if (dto.getUserId() == null || !game.getPlayerIds().contains(dto.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a player in this game");
        }

        boolean hasText   = dto.getText()   != null && !dto.getText().isBlank();
        boolean hasGifUrl = dto.getGifUrl() != null && !dto.getGifUrl().isBlank();

        if (!hasText && !hasGifUrl) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain text or gifUrl");
        }
        if (hasText && dto.getText().length() > MAX_TEXT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text exceeds " + MAX_TEXT_LENGTH + " characters");
        }
        if (hasGifUrl && !dto.getGifUrl().startsWith(KLIPY_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gifUrl must start with " + KLIPY_PREFIX);
        }

        ChatMessage message = new ChatMessage();
        message.setId(messageIdCounter.incrementAndGet());
        message.setGameId(gameId);
        message.setUserId(dto.getUserId());
        message.setUsername(dto.getUsername());
        message.setText(dto.getText());
        message.setGifUrl(dto.getGifUrl());
        message.setTimestamp(System.currentTimeMillis());

        chatCache.addGameMessage(gameId, message);
        return toDTO(message);
    }

    // Returns the full chat history for the given game.
    public List<ChatMessageGetDTO> getChatHistory(Long gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found: " + gameId);
        }
        return chatCache.getGameHistory(gameId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    // Helper method: Converts the internal ChatMessage entity to the DTO sent to the client.
    private ChatMessageGetDTO toDTO(ChatMessage msg) {
        ChatMessageGetDTO dto = new ChatMessageGetDTO();
        dto.setId(msg.getId());
        dto.setGameId(msg.getGameId());
        dto.setUserId(msg.getUserId());
        dto.setUsername(msg.getUsername());
        dto.setText(msg.getText());
        dto.setGifUrl(msg.getGifUrl());
        dto.setTimestamp(msg.getTimestamp());
        return dto;
    }
}
