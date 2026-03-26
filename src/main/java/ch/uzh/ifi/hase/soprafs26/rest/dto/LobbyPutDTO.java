package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class LobbyPutDTO {

    private String name;
    private String gameMode;
    private String map;
    private int startAbilities;
    private int maxPlayers;
    private String theme;

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}

    public String getGameMode() {return gameMode;}
    public void setGameMode(String gameMode) {this.gameMode = gameMode;}

    public String getMap() {return map;}
    public void setMap(String map) {this.map = map;}

    public int getStartAbilities() {return startAbilities;}
    public void setStartAbilities(int startAbilities) {this.startAbilities = startAbilities;}

    public int getMaxPlayers() {return maxPlayers;}
    public void setMaxPlayers(int maxPlayers) {this.maxPlayers = maxPlayers;}

    public String getTheme() {return theme;}
    public void setTheme(String theme) {this.theme = theme;}
    
}
