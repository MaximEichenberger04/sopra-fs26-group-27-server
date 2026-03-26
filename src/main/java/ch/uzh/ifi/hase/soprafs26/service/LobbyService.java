package ch.uzh.ifi.hase.soprafs26.service;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class LobbyService {

    private final Logger log = LoggerFactory.getLogger(LobbyService.class);

	private final LobbyRepository lobbyRepository;

	public LobbyService(@Qualifier("lobbyRepository") LobbyRepository lobbyRepository) {
		this.lobbyRepository = lobbyRepository;
	}

	public Lobby createLobby(Lobby newLobby) {
        if (newLobby.getName() == null || newLobby.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby name must not be empty");
        }

        if (newLobby.getHostId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HostId must not be null");
        }

        if (newLobby.getPlayerCount() == null || newLobby.getPlayerCount() != 2 || newLobby.getPlayerCount() != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player count must be 2 or 4");
        }

		newLobby.setStatus(LobbyStatus.WAITING);
		newLobby.setCurrentPlayers(1);
        newLobby.setInviteCode(generateInviteCode());

		newLobby = lobbyRepository.save(newLobby);
		lobbRepository.flush();

		log.debug("Created Lobby: {}", newLobby);
		return newLobby;
	}

    public List<Lobby> getLobbies() {
        return this.lobbyRepository.findAll();
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
    
    // HELPER METHOD FOR CREATELOBBY
    public String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(); // generate UUID and take the first 6 chars
    }
}
    