package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Stores the result of one completed game from one player's perspective.
 * One row is inserted per player per finished game.
 */
@Entity
@Table(name = "match_history")
public class MatchHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    /** The user this record belongs to. */
    @Column(nullable = false)
    private Long userId;

    /** The finished game. */
    @Column(nullable = false)
    private Long gameId;

    /** Comma-separated usernames of all opponents. */
    @Column(nullable = false)
    private String opponentUsernames;

    /** Game mode (e.g. "CLASSIC", "CHAOS"). */
    @Column(nullable = false)
    private String gameMode;

    /** true = this user won, false = this user lost. */
    @Column(nullable = false)
    private boolean won;

    @Column(nullable = false)
    private LocalDateTime playedAt;

    // ── getters / setters ──────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public String getOpponentUsernames() { return opponentUsernames; }
    public void setOpponentUsernames(String opponentUsernames) { this.opponentUsernames = opponentUsernames; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public boolean isWon() { return won; }
    public void setWon(boolean won) { this.won = won; }

    public LocalDateTime getPlayedAt() { return playedAt; }
    public void setPlayedAt(LocalDateTime playedAt) { this.playedAt = playedAt; }
}