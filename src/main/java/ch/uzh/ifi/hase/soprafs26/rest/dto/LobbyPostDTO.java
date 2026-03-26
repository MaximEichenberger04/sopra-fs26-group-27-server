package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LobbyPostDTO {
    
    private String name;
    private String gameMode;
    private int maxPlayers;
    private String theme;
    private String map;
    private int startAbilities;

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

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public int getStartAbilities() {
        return startAbilities;
    }   

    public void setStartAbilities(int startAbilities) {
        this.startAbilities = startAbilities;
    }
}
