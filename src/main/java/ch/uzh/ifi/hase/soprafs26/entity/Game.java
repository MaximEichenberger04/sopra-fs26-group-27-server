package ch.uzh.ifi.hase.soprafs26.entity;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "games")
public class Game implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long lobbyId;

    @Column(nullable = false)
    private GameStatus gameStatus;

    @Column(nullable = false)
    private int sizeBoard; // logical size (9 → 17×17 internal grid)

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private Long currentTurnUserId;

    @Column(nullable = false)
    private int wallsPerPlayer; // 10 for 2-player, 5 for 4-player

    @Column(nullable = true)
    private Long winnerId;

    @ElementCollection
    @CollectionTable(name = "game_players", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "user_id")
    private List<Long> playerIds = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLobbyId() { return lobbyId; }
    public void setLobbyId(Long lobbyId) { this.lobbyId = lobbyId; }

    public GameStatus getGameStatus() { return gameStatus; }
    public void setGameStatus(GameStatus gameStatus) { this.gameStatus = gameStatus; }

    public int getSizeBoard() { return sizeBoard; }
    public void setSizeBoard(int sizeBoard) { this.sizeBoard = sizeBoard; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public Long getCurrentTurnUserId() { return currentTurnUserId; }
    public void setCurrentTurnUserId(Long currentTurnUserId) { this.currentTurnUserId = currentTurnUserId; }

    public int getWallsPerPlayer() { return wallsPerPlayer; }
    public void setWallsPerPlayer(int wallsPerPlayer) { this.wallsPerPlayer = wallsPerPlayer; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public List<Long> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<Long> playerIds) { this.playerIds = playerIds; }
}
