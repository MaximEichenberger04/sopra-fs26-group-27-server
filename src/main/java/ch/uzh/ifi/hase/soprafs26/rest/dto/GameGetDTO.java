package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
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
    private String mapTheme;

    // Embedded board state
    private List<PawnGetDTO> pawns;
    private List<WallGetDTO> walls;
    private Map<Long, Integer> remainingWalls;

    // Chaos mode
    private boolean chaosMode;
    private List<AbilityType> myInventory;
    private List<PoisonZoneDTO> poisonZones;
    private List<Long> frozenPlayerIds;
    private boolean canDrawCard;
    private int turnCounter;
  
    private List<Long> activePlayerIds; // need this for automatic disconnect logic

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public List<Long> getActivePlayerIds() {
        return activePlayerIds;
    }

    public void setActivePlayerIds(List<Long> activePlayerIds) {
        this.activePlayerIds = activePlayerIds;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLobbyId() {
        return lobbyId;
    }

    public void setLobbyId(Long lobbyId) {
        this.lobbyId = lobbyId;
    }

    public GameStatus getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(GameStatus gameStatus) {
        this.gameStatus = gameStatus;
    }

    public String getMapTheme() { return mapTheme; }
    public void setMapTheme(String mapTheme) { this.mapTheme = mapTheme; }

    public List<Long> getActivePlayerIds() { return activePlayerIds; }
    public void setActivePlayerIds(List<Long> activePlayerIds) { this.activePlayerIds = activePlayerIds; }

    public List<PawnGetDTO> getPawns() { return pawns; }
    public void setPawns(List<PawnGetDTO> pawns) { this.pawns = pawns; }

    public void setSizeBoard(int sizeBoard) {
        this.sizeBoard = sizeBoard;
    }

    public Map<Long, Integer> getRemainingWalls() { return remainingWalls; }
    public void setRemainingWalls(Map<Long, Integer> remainingWalls) { this.remainingWalls = remainingWalls; }

    public boolean isChaosMode() { return chaosMode; }
    public void setChaosMode(boolean chaosMode) { this.chaosMode = chaosMode; }

    public List<AbilityType> getMyInventory() { return myInventory; }
    public void setMyInventory(List<AbilityType> myInventory) { this.myInventory = myInventory; }

    public List<PoisonZoneDTO> getPoisonZones() { return poisonZones; }
    public void setPoisonZones(List<PoisonZoneDTO> poisonZones) { this.poisonZones = poisonZones; }

    public List<Long> getFrozenPlayerIds() { return frozenPlayerIds; }
    public void setFrozenPlayerIds(List<Long> frozenPlayerIds) { this.frozenPlayerIds = frozenPlayerIds; }

    public boolean isCanDrawCard() { return canDrawCard; }
    public void setCanDrawCard(boolean canDrawCard) { this.canDrawCard = canDrawCard; }

    public int getTurnCounter() { return turnCounter; }
    public void setTurnCounter(int turnCounter) { this.turnCounter = turnCounter; }
}
    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Long getCurrentTurnUserId() {
        return currentTurnUserId;
    }

    public void setCurrentTurnUserId(Long currentTurnUserId) {
        this.currentTurnUserId = currentTurnUserId;
    }

    public int getWallsPerPlayer() {
        return wallsPerPlayer;
    }

    public void setWallsPerPlayer(int wallsPerPlayer) {
        this.wallsPerPlayer = wallsPerPlayer;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Long winnerId) {
        this.winnerId = winnerId;
    }

    public List<Long> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Long> playerIds) {
        this.playerIds = playerIds;
    }

    public List<PawnGetDTO> getPawns() {
        return pawns;
    }

    public void setPawns(List<PawnGetDTO> pawns) {
        this.pawns = pawns;
    }

    public List<WallGetDTO> getWalls() {
        return walls;
    }

    public void setWalls(List<WallGetDTO> walls) {
        this.walls = walls;
    }

    public Map<Long, Integer> getRemainingWalls() {
        return remainingWalls;
    }

    public void setRemainingWalls(Map<Long, Integer> remainingWalls) {
        this.remainingWalls = remainingWalls;
    }

    public String getMapTheme() {
        return mapTheme;
    }

    public void setMapTheme(String mapTheme) {
        this.mapTheme = mapTheme;
    }
}
