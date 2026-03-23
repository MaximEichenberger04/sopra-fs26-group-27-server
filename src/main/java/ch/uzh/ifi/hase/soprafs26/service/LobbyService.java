package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LobbyService {

    public List<Lobby> getLobbies() {
        return new ArrayList<>();
    }

    public Lobby createLobby(Lobby lobby, String token) {
        return lobby;
    }

    public Lobby getLobbyById(Long lobbyId) {
        return new Lobby();
    }

    public void updateLobby(Long lobbyId, Lobby lobby, String token) {
        // TODO
    }

    public void startLobby(Long lobbyId, String token) {
        // TODO
    }

    public Lobby joinLobby(String inviteCode, String token) {
        return new Lobby();
    }

    public void leaveLobby(Long lobbyId, String token) {
        // TODO
    }
}