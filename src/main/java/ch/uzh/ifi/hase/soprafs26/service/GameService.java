package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles game lifecycle: creation, retrieval, forfeit, win-condition, and turn advancement.
 *
 * Game metadata (status, playerIds, turn, winnerId) is persisted via JPA (Game entity).
 * Game state (pawn positions, walls) lives in GameStateCache, not in the DB.
 *
 * Starting positions on a 9×9 board (17×17 internal grid):
 *   Player 0: row=16, col=8  →  goal row = 0   (starts south, moves north)
 *   Player 1: row=0,  col=8  →  goal row = 16  (starts north, moves south)
 *
 * Wall budget: 10 per player.
 */
@Service
@Transactional
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final GameStateCache gameStateCache;
    private final GameWebSocketHandler gameWebSocketHandler;

    public GameService(
            @Qualifier("gameRepository")  GameRepository gameRepository,
            @Qualifier("lobbyRepository") LobbyRepository lobbyRepository,
            @Qualifier("userRepository")  UserRepository userRepository,
            GameStateCache gameStateCache,
            GameWebSocketHandler gameWebSocketHandler) {
        this.gameRepository       = gameRepository;
        this.lobbyRepository      = lobbyRepository;
        this.userRepository       = userRepository;
        this.gameStateCache       = gameStateCache;
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    // ─────────────────────────────────────────────────────────────
    //  Create
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a Game entity from an existing lobby, persists it, and initialises
     * the in-memory state via GameStateCache.
     * Sets lobby.gameId so the client can navigate to /games/{id}.
     */
    public Game createGameFromLobby(Long lobbyId, String token) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Read
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a GameGetDTO with embedded pawns and walls from the cache.
     */
    public GameGetDTO getGameById(Long gameId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Assembles a GameGetDTO from the Game entity (metadata) and
     * GameStateCache (pawns + walls).
     */
    public GameGetDTO buildGameGetDTO(Game game) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Forfeit
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks the game ENDED; the forfeiting player loses.
     * Evicts the game from the cache and broadcasts a final refresh.
     */
    public GameGetDTO forfeitGame(Long gameId, String token) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Win condition
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if the given player's pawn has reached its goal row/col.
     * Reads the pawn position from GameStateCache.
     *
     * Goal mapping (player index in game.playerIds):
     *   index 0 → goal row = 0
     *   index 1 → goal row = 16
     *   index 2 → goal col = 16
     *   index 3 → goal col = 0
     */
    public boolean checkWinCondition(Game game, Long userId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Turn management
    // ─────────────────────────────────────────────────────────────

    /**
     * Advances currentTurnUserId to the next player in the playerIds list (round-robin).
     * Persists the change to the Game entity.
     */
    public void advanceTurn(Game game) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }
}
