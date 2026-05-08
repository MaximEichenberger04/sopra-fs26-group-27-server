package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDateTime;

public class MatchHistoryGetDTO {

    private Long id;
    private Long gameId;
    private String opponentUsernames;
    private String gameMode;
    private boolean won;
    private LocalDateTime playedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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