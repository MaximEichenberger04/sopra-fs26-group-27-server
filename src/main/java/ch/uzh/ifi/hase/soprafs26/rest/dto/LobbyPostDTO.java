package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LobbyPostDTO {

    private String name;
    private String gameMode;
    private Integer maxPlayers;
    private Long hostId;
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

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    public String getMapTheme() {
        return mapTheme;
    }

    public void setMapTheme(String mapTheme) {
        this.mapTheme = mapTheme;
    }
}
