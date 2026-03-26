package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LobbyPutDTO {
    
    private String name;
    private String gameMode;
    private Integer playerCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }
}
