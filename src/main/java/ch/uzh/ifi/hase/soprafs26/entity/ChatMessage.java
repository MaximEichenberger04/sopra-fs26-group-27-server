package ch.uzh.ifi.hase.soprafs26.entity;

/**
 * Represents a single in-game chat message.
 * Plain POJO, not persisted to DB, held in GameStateCache.
 * Discarded when the game ends via evictGame.
 */
public class ChatMessage {

    private Long id;
    private Long gameId;
    private Long userId;
    private String username;
    private String text;
    private String gifUrl;
    private long timestamp;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}