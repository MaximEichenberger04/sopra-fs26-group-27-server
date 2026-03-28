package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LobbyService {

    private final Logger log = LoggerFactory.getLogger(LobbyService.class);

    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;

    public LobbyService(@Qualifier("lobbyRepository") LobbyRepository lobbyRepository,
                        @Qualifier("userRepository") UserRepository userRepository) {
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
    }

    public Lobby createLobby(Lobby newLobby, String token) {
        User authenticatedUser = userRepository.findByToken(token);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        if (newLobby.getHostId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HostId must not be null");
        }

        if (!authenticatedUser.getId().equals(newLobby.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only create a lobby for yourself!");
        }

        if (newLobby.getName() == null || newLobby.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby name must not be empty");
        }

        if (newLobby.getMaxPlayers() == null || (newLobby.getMaxPlayers() != 2 && newLobby.getMaxPlayers() != 4)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player count must be 2 or 4");
        }

        if (newLobby.getGameMode() == null || newLobby.getGameMode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game mode must not be empty");
        }

        newLobby.setLobbyStatus(LobbyStatus.WAITING);
        newLobby.setCurrentPlayers(1);
        newLobby.setInviteCode(generateInviteCode());

        newLobby = lobbyRepository.save(newLobby);
        lobbyRepository.flush();

        log.debug("Created Lobby: {}", newLobby);
        return newLobby;
    }

    public List<Lobby> getLobbies() {
        return this.lobbyRepository.findAll();
    }

    public Lobby getLobbyById(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId).orElse(null);

        if (lobby == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found");
        }

        return lobby;
    }

    public void updateLobby(Long lobbyId, Lobby lobbyChange, String token) {
        User authenticatedUser = userRepository.findByToken(token);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        Lobby existingLobby = lobbyRepository.findById(lobbyId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found")
        );

        if (!authenticatedUser.getId().equals(existingLobby.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can make changes to the lobby!");
        }

        if (lobbyChange.getName() != null && !lobbyChange.getName().isBlank()) {
            existingLobby.setName(lobbyChange.getName());
        }

        if (lobbyChange.getGameMode() != null && !lobbyChange.getGameMode().isBlank()) {
            existingLobby.setGameMode(lobbyChange.getGameMode());
        }

        if (lobbyChange.getMaxPlayers() != null) {
            if (lobbyChange.getMaxPlayers() != 2 && lobbyChange.getMaxPlayers() != 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player count must be 2 or 4");
            }
            existingLobby.setMaxPlayers(lobbyChange.getMaxPlayers());
        }

        lobbyRepository.save(existingLobby);
        lobbyRepository.flush();
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

    public String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}

    