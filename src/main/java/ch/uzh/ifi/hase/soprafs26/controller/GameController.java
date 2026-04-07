package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.MoveService;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games")
public class GameController {

    private final GameService gameService;
    private final MoveService moveService;
    private final GameWebSocketHandler webSocketHandler;

    GameController(GameService gameService, MoveService moveService, GameWebSocketHandler webSocketHandler) {
        this.gameService = gameService;
        this.moveService = moveService;
        this.webSocketHandler = webSocketHandler;
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
        Game game = gameService.getGameById(gameId);
        return DTOMapper.INSTANCE.converEntityToGameGetDTO(game);
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
        Game game = moveService.processMove(gameId, movePostDTO, token);
        webSocketHandler.broadcastGameEvent("MOVE", gameId);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
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
        Game game = moveService.applyWallPlacement(gameId, wallPostDTO, token);
        webSocketHandler.broadcastGameEvent("WALL", gameId);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
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
        Game game = gameService.forfeitGame(gameId, token);
        webSocketHandler.broadcastGameEvent("FORFEIT", gameId);
        return DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);
    }
}
