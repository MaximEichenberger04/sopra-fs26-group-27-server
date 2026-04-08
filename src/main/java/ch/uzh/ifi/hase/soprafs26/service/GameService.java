package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
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

import java.util.List;

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
        Lobby lobby = lobbyRepository.findById(lobbyId) // find lobby
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found"));

        User user = userRepository.findByToken(token); // find user
        if (user==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        // build game entity
        Game game = new Game();
        game.setLobbyId(lobbyId);
        game.setCreatorId(lobby.getHostid());
        game.setPlayerIds(new ArrayList<>(lobby.getPlayerIds()));
        game.setCurrenTurnUserId(lobby.getPlayerIds().get(0));
        game.setGameStatus(GameStatus.RUNNING);
        game.setSizeBoard(9); // standard logical size (9x9 fields for pawn)
        game.setWallsPerPlayer(lobby.getMaxPlayers() == 2 ? 10 : 5); // check if lobby has 2 or 4 players
        
        game = gameRepository.save(game);
        gameRepository.flush();

        gameStateCache.initGame(game.getId(), game.getPlayersIds());
        lobby.setGameId(game.getId());
        lobbyRepository.save(lobby);

        return game;
    }   

    // ─────────────────────────────────────────────────────────────
    //  Read
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a GameGetDTO with embedded pawns and walls from the cache.
     */
    public GameGetDTO getGameById(Long gameId) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        return buildGameGetDTO(game); // call build and return DTO
    }

    /**
     * Assembles a GameGetDTO from the Game entity (metadata) and
     * GameStateCache (pawns + walls).
     */
    public GameGetDTO buildGameGetDTO(Game game) {
        // convert entity to DTO
        GameGetDTO dto = DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);

        // manually attach pawns from the game cache
        List<PawnGetDTO> pawnDTOs = gameStateCache.getPawns(game.getId()).stream()
            .map(DTOMapper.INSTANCE::convertEntityToPawnGetDTO)
            .collect(Collectors.toList());
        dto.setPawns(pawnDTOs);

        // manually attach walls from game cache
        List<WallGetDTO> wallDTOs = gameStateCache.getWalls(game.getId()).stream()
            .map(DTOMapper.INSTANCE::convertEntityToWallGetDTO)
            .collect(Collectors.toList());
        dto.setWalls(wallDTOs);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    //  Forfeit
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks the game ENDED; the forfeiting player loses.
     * Evicts the game from the cache and broadcasts a final refresh.
     */
    public GameGetDTO forfeitGame(Long gameId, String token) {
        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        User user = userRepository.findByToken(token);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        
        // The other player wins
        Long winnerId = game.getPlayerIds().stream()
            .filter(id -> !id.equals(user.getId()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot forfeit"));

        game.setGameStatus(GameStatus.ENDED);
        game.setWinnerId(winnerId);
        gameRepository.save(game);

        // Free memory
        gameStateCache.evictGame(gameId);

        return buildGameGetDTO(game);
    }

    // ─────────────────────────────────────────────────────────────
    //  Win condition
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if the given player's pawn has reached its goal row/col.
     * Reads the pawn position from GameStateCache.
     *
     * Goal mapping (player index in game.playerIds):
     *   index 0 => goal row = 0
     *   index 1 => goal row = 16
     */
    public boolean checkWinCondition(Game game, Long userId) {
        int index = game.getPlayerIds().indexOf(userId);
        if (index < 0) return false;

        Pawn pawn = gameStateCache.getPawn(game.getId(), userId);
        if (pawn == null) return false;

        if (index == 0) return pawn.getRow() == 0;  //index 0 => goal row = 0
        if (index == 1) return pawn.getRow() == 16; //index 1 => goal row = 16

        return false;
    }

    // ─────────────────────────────────────────────────────────────
    //  Turn management
    // ─────────────────────────────────────────────────────────────

    /**
     * Advances currentTurnUserId to the next player in the playerIds list (round-robin).
     * Persists the change to the Game entity.
     */
    public void advanceTurn(Game game) {
        List<Long> players = game.getPlayerIds();
        int index = players.indexOf(game.getCurrentTurnUserId());
        int next = (index + 1) % players.size();
        game.setCurrentTurnUserId(players.get(next));
        gameRepository.saveAndFlush(game);
    }

    // Ends the game with the given winner, evicts cache, broadcasts GAME_OVER.
    public void endGame(Game game, Long winnerId) {
        game.setWinnerId(winnerId);
        game.setGameStatus(GameStatus.ENDED);
        gameRepository.saveAndFlush(game);
        gameStateCache.evictGame(game.getId());
        gameWebSocketHandler.broadcastGameEvent("GAME_OVER", game.getId());
    }
}
