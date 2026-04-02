package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "chat_messages")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String gifUrl;

    @Column(nullable = false)
    private long timestamp; // epoch millis

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
