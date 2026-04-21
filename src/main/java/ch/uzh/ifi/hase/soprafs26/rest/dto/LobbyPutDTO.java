package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LobbyPutDTO {

    private String name;
    private String gameMode;
    private Integer maxPlayers;
    private String mapTheme;

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

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getMapTheme() {
        return mapTheme;
    }

    public void setMapTheme(String mapTheme) {
        this.mapTheme = mapTheme;
    }
}
