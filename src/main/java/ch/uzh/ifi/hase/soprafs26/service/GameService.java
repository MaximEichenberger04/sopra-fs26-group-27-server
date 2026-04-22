package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PawnGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;

/**
 * Handles game lifecycle: creation, retrieval, forfeit, win-condition, and turn
 * advancement.
 *
 * Game metadata (status, playerIds, turn, winnerId) is persisted via JPA (Game
 * entity).
 * Game state (pawn positions, walls) lives in GameStateCache, not in the DB.
 *
 * Starting positions on a 9×9 board (17×17 internal grid):
 * Player 0: row=16, col=8 → goal row = 0 (starts south, moves north)
 * Player 1: row=0, col=8 → goal row = 16 (starts north, moves south)
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
    private final ChatCache chatCache;

    public GameService(
            @Qualifier("gameRepository") GameRepository gameRepository,
            @Qualifier("lobbyRepository") LobbyRepository lobbyRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            GameStateCache gameStateCache,
            ChatCache chatCache) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.gameStateCache = gameStateCache;
        this.chatCache = chatCache;
    }

    // ─────────────────────────────────────────────────────────────
    // Create
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
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");

        // build game entity
        Game game = new Game();
        game.setLobbyId(lobbyId);
        game.setCreatorId(lobby.getHostId());
        game.setPlayerIds(new ArrayList<>(lobby.getPlayerIds()));
        game.setActivePlayerIds(new ArrayList<>(lobby.getPlayerIds())); // automatic disconnect logic
        game.setCurrentTurnUserId(lobby.getPlayerIds().get(0));
        game.setGameStatus(GameStatus.RUNNING);
        game.setSizeBoard(9); // standard logical size (9x9 fields for pawn)
        game.setWallsPerPlayer(lobby.getMaxPlayers() == 2 ? 10 : 5); // check if lobby has 2 or 4 players

        // ---> TRANSFERS MAP THEME FROM LOBBY TO GAME <---
        game.setMapTheme(lobby.getMapTheme());

        game = gameRepository.save(game);
        gameRepository.flush();

        gameStateCache.initGame(game.getId(), game.getPlayerIds());
        chatCache.initGame(game.getId());
        lobby.setGameId(game.getId());
        lobbyRepository.save(lobby);

        return game;
    }

    // ─────────────────────────────────────────────────────────────
    // Read
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

        // compute remaining wall budget per player
        Map<Long, Long> usedWalls = gameStateCache.getWalls(game.getId()).stream()
                .collect(Collectors.groupingBy(Wall::getUserId, Collectors.counting()));

        Map<Long, Integer> remainingWalls = new HashMap<>();
        for (Long playerId : game.getPlayerIds()) {
            int used = usedWalls.getOrDefault(playerId, 0L).intValue();
            remainingWalls.put(playerId, game.getWallsPerPlayer() - used);
        }
        dto.setRemainingWalls(remainingWalls);

        return dto;
    }

    // ─────────────────────────────────────────────────────────────
    // Forfeit
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks the game ENDED; the forfeiting player loses.
     * Evicts the game from the cache and broadcasts a final refresh.
     */
    public GameGetDTO forfeitGame(Long gameId, String token) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        User user = userRepository.findByToken(token);
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");

        if (game.getGameStatus() == GameStatus.ENDED) {
            return buildGameGetDTO(game);
        }

        removePlayerFromGame(game, user.getId());

        if (game.getActivePlayerIds().size() == 1) {
            Long winnerId = game.getActivePlayerIds().get(0);
            return endGame(game, winnerId);
        }

        if (game.getCurrentTurnUserId().equals(user.getId())) {
            advanceTurn(game, user.getId());
        } else {
            gameRepository.saveAndFlush(game);
        }

        return buildGameGetDTO(game);
    }

    private void removePlayerFromGame(Game game, Long userId) {
        List<Long> activePlayers = new ArrayList<>(game.getActivePlayerIds());
        activePlayers.remove(userId);
        game.setActivePlayerIds(activePlayers);
    }

    public GameGetDTO forfeitDisconnectedPlayer(Long gameId, Long disconnectedUserId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        if (game.getGameStatus() == GameStatus.ENDED) {
            return buildGameGetDTO(game);
        }

        if (!game.getActivePlayerIds().contains(disconnectedUserId)) {
            return buildGameGetDTO(game);
        }

        removePlayerFromGame(game, disconnectedUserId);

        if (game.getActivePlayerIds().size() == 1) {
            Long winnerId = game.getActivePlayerIds().get(0);
            return endGame(game, winnerId);
        }

        if (game.getCurrentTurnUserId().equals(disconnectedUserId)) {
            advanceTurn(game, disconnectedUserId);
        } else {
            gameRepository.saveAndFlush(game);
        }

        return buildGameGetDTO(game);
    }

    // ─────────────────────────────────────────────────────────────
    // Win condition
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if the given player's pawn has reached its goal row/col.
     * Reads the pawn position from GameStateCache.
     *
     * Goal mapping (player index in game.playerIds):
     * index 0 => goal row = 0
     * index 1 => goal row = 16
     */
    public boolean checkWinCondition(Game game, Long userId) {
        int index = game.getPlayerIds().indexOf(userId);
        if (index < 0)
            return false;

        Pawn pawn = gameStateCache.getPawn(game.getId(), userId);
        if (pawn == null)
            return false;

        if (index == 0)
            return pawn.getRow() == 0; // index 0 => goal row = 0
        if (index == 1)
            return pawn.getRow() == 16; // index 1 => goal row = 16
        if (index == 2)
            return pawn.getCol() == 0; // index 3 => starts on right side -> goal col = 0
        if (index == 3)
            return pawn.getCol() == 16;

        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Turn management
    // ─────────────────────────────────────────────────────────────

    /**
     * Advances currentTurnUserId to the next player in the playerIds list
     * (round-robin).
     * Persists the change to the Game entity.
     */
    public void advanceTurn(Game game) {
        advanceTurn(game, null);
    }

    public void advanceTurn(Game game, Long removedUserId) {
        List<Long> activePlayers = game.getActivePlayerIds();
        if (activePlayers == null || activePlayers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active players left");
        }

        Long currentTurnUserId = game.getCurrentTurnUserId();
        int index = activePlayers.indexOf(currentTurnUserId);

        if (index != -1) {
            int next = (index + 1) % activePlayers.size();
            game.setCurrentTurnUserId(activePlayers.get(next));
            gameRepository.saveAndFlush(game);
            return;
        }

        if (removedUserId != null) {
            List<Long> originalOrder = game.getPlayerIds();
            int removedIndex = originalOrder.indexOf(removedUserId);
            if (removedIndex == -1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Removed player not found in original order");
            }

            for (int step = 1; step <= originalOrder.size(); step++) {
                Long candidate = originalOrder.get((removedIndex + step) % originalOrder.size());
                if (activePlayers.contains(candidate)) {
                    game.setCurrentTurnUserId(candidate);
                    gameRepository.saveAndFlush(game);
                    return;
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine next active player");
    }

    // Ends the game with the given winner, evicts cache, broadcasts GAME_OVER.
    public GameGetDTO endGame(Game game, Long winnerId) {
        game.setWinnerId(winnerId);
        game.setGameStatus(GameStatus.ENDED);
        gameRepository.saveAndFlush(game);
        GameGetDTO dto = buildGameGetDTO(game);
        gameStateCache.evictGame(game.getId());
        chatCache.evictGame(game.getId());
        return dto;
    }
}