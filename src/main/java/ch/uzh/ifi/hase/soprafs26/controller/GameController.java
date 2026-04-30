package ch.uzh.ifi.hase.soprafs26.controller;
 
import ch.uzh.ifi.hase.soprafs26.rest.dto.AbilityPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.AbilityService;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.MoveService;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
 
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games")
public class GameController {

    private final GameService gameService;
    private final MoveService moveService;
    private final AbilityService abilityService;
    private final GameWebSocketHandler webSocketHandler;
    private final UserRepository userRepository;

    GameController(GameService gameService, MoveService moveService, 
            AbilityService abilityService, UserRepository userRepository, GameWebSocketHandler webSocketHandler) {
        this.gameService = gameService;
        this.moveService = moveService;
        this.abilityService = abilityService;
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
        Long requestingUserId = resolveUserId(token);
        return gameService.getGameById(gameId, requestingUserId); //DTO already assembled in GameService through buildGameGetDTO
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
        webSocketHandler.broadcastGameEvent("FORFEIT", gameId);
        webSocketHandler.broadcastGameEvent("GAME_OVER", gameId);
        return result;
    }

    /**
     * POST /games/{gameId}/ability
     * Use an ability from the calling player's inventory
    */

    @PostMapping("/{gameId}/ability")
    @ResponseStatus(HttpStatus.OK)
    public GameGetDTO useAbility(
            @PathVariable Long gameId,
            @RequestBody AbilityPostDTO abilityPostDTO,
            @RequestHeader("Authorization") String token) {
        GameGetDTO result = abilityService.useAbility(gameId, abilityPostDTO, token);
        webSocketHandler.broadcastGameEvent("ABILITY_USED", gameId);
        return result;
    }

    /**
     * POST /games/{gameId}/ability/draw
     * Draw a card from the deck into the calling player's inventory
    */

    @PostMapping("/{gameId}/ability/draw")
    @ResponseStatus(HttpStatus.OK)
    public GameGetDTO drawAbilityCard(
            @PathVariable Long gameId,
            @RequestHeader("Authorization") String token) {
        GameGetDTO result = abilityService.drawCard(gameId, token);
        webSocketHandler.broadcastGameEvent("ABILITY_DRAW", gameId);
        return result;
    }

    // Resolves a token to its userId, returns null if the token is invalid.
    private Long resolveUserId(String token) {
        User user = userRepository.findByToken(token);
        return user != null ? user.getId() : null;
    }
}
