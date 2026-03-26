package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;

import java.util.List;

public class LobbyGetDTO {

    private Long lobbyId;
    private String name;
    private String inviteCode;
    private String theme;
    private int maxPlayers;
    private Long hostId;
    private int currentPlayers;
    private int lobbyStatus;
    private String gameMode;
    private String map;
    private int startAbilities;
    private List<Long> playerIds;

    public Long getLobbyId() {return lobbyId;}
    public void setLobbyId(Long lobbyId) {this.lobbyId = lobbyId;}

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}

    public String getInviteCode() {return inviteCode;}
    public void setInviteCode(String inviteCode) {this.inviteCode = inviteCode;}

    public String getTheme() {return theme;}
    public void setTheme(String theme) {this.theme = theme;}

    public int getMaxPlayers() {return maxPlayers;}
    public void setMaxPlayers(int maxPlayers) {this.maxPlayers = maxPlayers;}

    public Long getHostId() {return hostId;}
    public void setHostId(Long hostId) {this.hostId = hostId;}

    public int getCurrentPlayers() {return currentPlayers;}
    public void setCurrentPlayers(int currentPlayers) {this.currentPlayers = currentPlayers;}

    public LobbyStatus getLobbyStatus() {return lobbyStatus;}
    public void setLobbyStatus(LobbyStatus lobbyStatus) {this.lobbyStatus = lobbyStatus;}

    public String getGameMode() {return gameMode;}
    public void setGameMode(String gameMode) {this.gameMode = gameMode;}

    public String getMap() {return map;}
    public void setMap(String map) {this.map = map;}

    public int getStartAbilities() {return startAbilities;}
    public void setStartAbilities(int startAbilities) {this.startAbilities = startAbilities;}

    public List<Long> getPlayerIds() {return playerIds;}
    public void setPlayerIds(List<Long> playerIds) {this.playerIds = playerIds;}

    
}
