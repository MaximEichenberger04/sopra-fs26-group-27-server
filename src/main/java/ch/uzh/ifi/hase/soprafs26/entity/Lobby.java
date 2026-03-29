package ch.uzh.ifi.hase.soprafs26.entity;
import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import jakarta.persistence.*;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import java.io.Serializable;
import java.lang.annotation.Inherited;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lobbies")
public class Lobby implements Serializable {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name; // lobby name

	@Column(nullable = false)
	private LobbyStatus lobbyStatus; 

    @Column(nullable = false)
    private Long hostId; // user id from host

    @Column(nullable = false)
    private Integer maxPlayers;

    @Column(nullable = false)
    private Integer currentPlayers;

    @Column(nullable = false)
    private String gameMode;

    @Column(nullable = false, unique = true)
    private String inviteCode;

    @ElementCollection
    @CollectionTable(name = "lobby_players", joinColumns = @JoinColumn(name = "lobby_id"))
    @Column(name = "user_id")
    private List<Long> playerIds = new ArrayList<>();

    public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LobbyStatus getLobbyStatus() {
        return lobbyStatus;
    }

    public void setLobbyStatus(LobbyStatus lobbyStatus) {
        this.lobbyStatus = lobbyStatus;
    }

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
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

    public Integer getCurrentPlayers() {
        return currentPlayers;
    }

    public void setCurrentPlayers(Integer currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public List<Long> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Long> playerIds) {
        this.playerIds = playerIds;
    }
}
