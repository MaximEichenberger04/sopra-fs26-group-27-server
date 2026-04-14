package ch.uzh.ifi.hase.soprafs26.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import ch.uzh.ifi.hase.soprafs26.service.LobbyService;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPutDTO;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/lobbies")
public class LobbyController {
    
	private final LobbyService lobbyService;

	LobbyController(LobbyService lobbyService) {
		this.lobbyService = lobbyService;
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
    public List<LobbyGetDTO> getAllLobbies() {

        List<Lobby> lobbies = lobbyService.getLobbies();
        List<LobbyGetDTO> lobbyGetDTOs = new ArrayList<>();

        for (Lobby lobby : lobbies) {
            lobbyGetDTOs.add(DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby));
        }

        return lobbyGetDTOs;
    }

    /**
     * POST /lobbies
     * Create a new lobby
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public LobbyGetDTO createLobby(
            @RequestBody LobbyPostDTO lobbyPostDTO,
            @RequestHeader("Authorization") String token) {

        Lobby lobbyInput = DTOMapper.INSTANCE.convertLobbyPostDTOtoEntity(lobbyPostDTO);

        Lobby createdLobby = lobbyService.createLobby(lobbyInput, token);

        return DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(createdLobby);
    }

    /**
     * GET /lobbies/{lobbyId}
     * Used for polling (waiting room)
     */
    @GetMapping("/{lobbyId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public LobbyGetDTO getLobby(@PathVariable Long lobbyId) {

        Lobby lobby = lobbyService.getLobbyById(lobbyId);

        return DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby);
    }

    /**
     * PUT /lobbies/{lobbyId}
     * Update lobby settings (host only)
     */
    @PutMapping("/{lobbyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLobby(
            @PathVariable Long lobbyId,
            @RequestBody LobbyPutDTO lobbyPutDTO,
            @RequestHeader("Authorization") String token) {

        Lobby lobbyInput = DTOMapper.INSTANCE.convertLobbyPutDTOtoEntity(lobbyPutDTO);

        lobbyService.updateLobby(lobbyId, lobbyInput, token);
    }

    /**
     * POST /lobbies/{lobbyId}/start
     * Start game from lobby; returns the created GameGetDTO so the client gets the gameId.
     */
    @PostMapping("/{lobbyId}/start")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public GameGetDTO startLobby(
            @PathVariable Long lobbyId,
            @RequestHeader("Authorization") String token) {

        return lobbyService.startLobby(lobbyId, token);
    }

    /**
     * POST /lobbies/join/{inviteCode}
     * Join lobby via invite code
     */
    @PostMapping("/join/{inviteCode}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public LobbyGetDTO joinLobbyByInviteCode(
            @PathVariable String inviteCode,
            @RequestHeader("Authorization") String token) {

        Lobby lobby = lobbyService.joinLobbyByInviteCode(inviteCode, token);

        return DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby);
    }


    /**
     * POST /lobbies/{lobbyId}/join
     * Join open lobby via lobbies page
     */
    @PostMapping("/{lobbyId}/join")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public LobbyGetDTO joinLobbyById(
            @PathVariable Long lobbyId,
            @RequestHeader("Authorization") String token) {

        Lobby lobby = lobbyService.joinLobbyById(lobbyId, token);
        return DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby);
    }

    /**
     * POST /lobbies/{lobbyId}/leave
     * Leave a lobby
     */
    @PostMapping("/{lobbyId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveLobby(
            @PathVariable Long lobbyId,
            @RequestHeader("Authorization") String token) {

        lobbyService.leaveLobby(lobbyId, token);
    }
}
