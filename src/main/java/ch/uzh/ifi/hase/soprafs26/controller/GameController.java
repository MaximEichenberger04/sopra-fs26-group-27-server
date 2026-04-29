package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.MoveService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games")
public class GameController {

    private final GameService gameService;
    private final MoveService moveService;
    private final GameWebSocketHandler webSocketHandler;
    private final UserRepository userRepository;

    GameController(GameService gameService, MoveService moveService, GameWebSocketHandler webSocketHandler,
            UserRepository userRepository) {
        this.gameService = gameService;
        this.moveService = moveService;
        this.webSocketHandler = webSocketHandler;
        this.userRepository = userRepository;
    }

    /**
     * GET /games/{gameId}
     * Returns the current state of the game (pawns + walls included).
     */
    @GetMapping("/{gameId}")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getGame(
            @PathVariable Long gameId,
            @RequestHeader("Authorization") String token) {
        return gameService.getGameById(gameId); //DTO already assembled in GameService through buildGameGetDTO
    }

    /**
     * POST /games/{gameId}/move
     * Submit a pawn move for the authenticated player.
     */
    @PostMapping("/{gameId}/move")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO submitMove(
            @PathVariable Long gameId,
            @RequestBody MovePostDTO movePostDTO,
            @RequestHeader("Authorization") String token) {
        GameGetDTO result = moveService.processMove(gameId, movePostDTO, token);
        if (result.getWinnerId() != null) {
            webSocketHandler.broadcastGameEvent("GAME_OVER", gameId);
        } else {
            webSocketHandler.broadcastGameEvent("MOVE", gameId);
        }
        return result;
    }

    /**
     * POST /games/{gameId}/wall
     * Place a wall for the authenticated player.
     */
    @PostMapping("/{gameId}/wall")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO submitWall(
            @PathVariable Long gameId,
            @RequestBody WallPostDTO wallPostDTO,
            @RequestHeader("Authorization") String token) {
        GameGetDTO result = moveService.applyWallPlacement(gameId, wallPostDTO, token);
        webSocketHandler.broadcastGameEvent("WALL", gameId);
        return result;
    }

    /**
     * POST /games/{gameId}/forfeit
     * Forfeit the game; the requesting player loses.
     */
    @PostMapping("/{gameId}/forfeit")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO forfeitGame(
            @PathVariable Long gameId,
            @RequestHeader("Authorization") String token) {
        GameGetDTO result = gameService.forfeitGame(gameId, token);
        User user = userRepository.findByToken(token);
        if (user != null) {
            webSocketHandler.broadcastGameEvent("PLAYER_FORFEITED", gameId, user.getId(), null);
        } else {
            webSocketHandler.broadcastGameEvent("FORFEIT", gameId);
        }
        if (result.getWinnerId() != null) {
            webSocketHandler.broadcastGameEvent("GAME_OVER", gameId);
        } else {
            webSocketHandler.broadcastGameEvent("GAME_UPDATED", gameId);
        }
        return result;
    }
}
