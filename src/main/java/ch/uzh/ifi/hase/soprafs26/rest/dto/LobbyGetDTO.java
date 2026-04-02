package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;

import java.util.List;

public class LobbyGetDTO {

    private Long id;
    private String name;
    private String inviteCode;
    private int maxPlayers;
    private Long hostId;
    private int currentPlayers;
    private LobbyStatus lobbyStatus;
    private String gameMode;
    private List<Long> playerIds;

    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}

    public String getInviteCode() {return inviteCode;}
    public void setInviteCode(String inviteCode) {this.inviteCode = inviteCode;}

    public int getMaxPlayers() {return maxPlayers;}
    public void setMaxPlayers(int maxPlayers) {this.maxPlayers = maxPlayers;}

    public Long getHostId() {return hostId;}
    public void setHostId(Long hostId) {this.hostId = hostId;}

    public int getCurrentPlayers() {return currentPlayers;}
    public void setCurrentPlayers(int currentPlayers) {this.currentPlayers = currentPlayers;}

    public LobbyStatus getLobbyStatus() { return lobbyStatus; }  // returns enum
    public void setLobbyStatus(LobbyStatus lobbyStatus) { this.lobbyStatus = lobbyStatus; }  // sets enum

    public String getGameMode() {return gameMode;}
    public void setGameMode(String gameMode) {this.gameMode = gameMode;}

    public List<Long> getPlayerIds() {return playerIds;}
    public void setPlayerIds(List<Long> playerIds) {this.playerIds = playerIds;}

    
}
