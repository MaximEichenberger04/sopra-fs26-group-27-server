package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;

import java.util.List;
import java.util.Map;

public class GameGetDTO {

    private Long id;
    private Long lobbyId;
    private GameStatus gameStatus;
    private int sizeBoard;
    private Long creatorId;
    private Long currentTurnUserId;
    private int wallsPerPlayer;
    private Long winnerId;
    private List<Long> playerIds;

    // Embedded board state, populated manually in GameService, not by MapStruct
    private List<PawnGetDTO> pawns;
    private List<WallGetDTO> walls;
    private Map<Long, Integer> remainingWalls;

    private List<Long> activePlayerIds; // need this for automatic disconnect logic

    public List<Long> getActivePlayerIds() { return activePlayerIds; }
    public void setActivePlayerIds(List<Long> activePlayerIds) { this.activePlayerIds = activePlayerIds; }

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

    public List<PawnGetDTO> getPawns() { return pawns; }
    public void setPawns(List<PawnGetDTO> pawns) { this.pawns = pawns; }

    public List<WallGetDTO> getWalls() { return walls; }
    public void setWalls(List<WallGetDTO> walls) { this.walls = walls; }

    public Map<Long, Integer> getRemainingWalls() { return remainingWalls; }
    public void setRemainingWalls(Map<Long, Integer> remainingWalls) { this.remainingWalls = remainingWalls; }
}
