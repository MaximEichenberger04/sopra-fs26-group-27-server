package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.entity.PoisonZone;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.MatchHistory;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.repository.MatchHistoryRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PawnGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PoisonZoneDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.mapper.DTOMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

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

    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final GameStateCache gameStateCache;
    private final ChatCache chatCache;
    private final MatchHistoryRepository matchHistoryRepository;
    private final UserService userService;
    private final LevelingService levelingService;
    private static final int DEFAULT_TURN_TIME_LIMIT_SECONDS = 30;

    public GameService(
            @Qualifier("gameRepository") GameRepository gameRepository,
            @Qualifier("lobbyRepository") LobbyRepository lobbyRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            GameStateCache gameStateCache,
            ChatCache chatCache,
            MatchHistoryRepository matchHistoryRepository,
            UserService userService,
            LevelingService levelingService) {
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.gameStateCache = gameStateCache;
        this.chatCache = chatCache;
        this.matchHistoryRepository = matchHistoryRepository;
        this.userService = userService;
        this.levelingService = levelingService;
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
        game.setTurnTimeLimitSeconds(DEFAULT_TURN_TIME_LIMIT_SECONDS);
        resetTurnTimer(game);
        game.setSizeBoard(9); // standard logical size (9x9 fields for pawn)
        game.setWallsPerPlayer(lobby.getMaxPlayers() == 2 ? 10 : 5); // check if lobby has 2 or 4 players
        game.setChaosMode("CHAOS".equalsIgnoreCase(lobby.getGameMode()));

        // ---> TRANSFERS MAP THEME FROM LOBBY TO GAME <---
        game.setMapTheme(lobby.getMapTheme());

        game = gameRepository.save(game);
        gameRepository.flush();

        gameStateCache.initGame(game.getId(), game.getPlayerIds(), game.isChaosMode());
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
    public GameGetDTO getGameById(Long gameId, Long requestingUserId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

         GameGetDTO timeoutResult = enforceTimeoutIfExpired(game);
        if (timeoutResult != null) {
            return timeoutResult;
        }

        return buildGameGetDTO(game, requestingUserId); // call build and return DTO
    }

    /**
     * Assembles a GameGetDTO from the Game entity (metadata) and
     * GameStateCache (pawns + walls).
     */
    public GameGetDTO buildGameGetDTO(Game game) {
        return buildGameGetDTO(game, null);
    }

    /**
     * Assembles a GameGetDTO from the Game entity (metadata) and
     * GameStateCache (pawns + walls). Populates myInventory and canDrawCard
     * for the requesting player when requestingUserId is provided.
     */
    public GameGetDTO buildGameGetDTO(Game game, Long requestingUserId) {
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

        // Chaos mode extras
        if (game.isChaosMode()) {
            dto.setChaosMode(true);
            dto.setTurnCounter(gameStateCache.getTurnCounter(game.getId()));

            // Poison zones (all players see these)
            List<PoisonZoneDTO> zoneDTOs = new ArrayList<>();
            for (PoisonZone zone : gameStateCache.getPoisonZones(game.getId())) {
                PoisonZoneDTO zoneDTO = new PoisonZoneDTO();
                zoneDTO.setId(zone.getId());
                zoneDTO.setTopLeftRow(zone.getTopLeftRow());
                zoneDTO.setTopLeftCol(zone.getTopLeftCol());
                zoneDTO.setRoundsRemaining(zone.getRoundsRemaining());
                zoneDTOs.add(zoneDTO);
            }
            dto.setPoisonZones(zoneDTOs);

            // Extra wall budget per player
            for (Long playerId : game.getPlayerIds()) {
                int extra = gameStateCache.getExtraWalls(game.getId(), playerId);
                if (extra > 0) {
                    remainingWalls.merge(playerId, extra, Integer::sum);
                }
            }

            // Which players are frozen (skip their next turn)
            List<Long> frozenIds = game.getPlayerIds().stream()
                    .filter(pid -> gameStateCache.isFrozen(game.getId(), pid))
                    .collect(Collectors.toList());
            dto.setFrozenPlayerIds(frozenIds);

            // Personal fields – only visible to the requesting player
            if (requestingUserId != null) {
                dto.setMyInventory(gameStateCache.getInventory(game.getId(), requestingUserId));
                dto.setCanDrawCard(gameStateCache.hasPendingCardDraw(game.getId(), requestingUserId));
            }
        }
        dto.setServerTimeMillis(System.currentTimeMillis());
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
        // Record elimination order for 4-player placement
        gameStateCache.recordElimination(game.getId(), userId);
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
            Long nextPlayer = activePlayers.get(next);
            game.setCurrentTurnUserId(nextPlayer);
            resetTurnTimer(game);
            gameRepository.saveAndFlush(game);

            // Auto-skip frozen player if they have no walls and no ability cards
            if (game.isChaosMode() && gameStateCache.isFrozen(game.getId(), nextPlayer)) {
                int walls = gameStateCache.getExtraWalls(game.getId(), nextPlayer);
                // Count base walls remaining (stored in remainingWalls map via MoveService)
                // We check if they have any ability cards — if not and no walls, skip
                boolean hasAbilities = !gameStateCache.getInventory(game.getId(), nextPlayer).isEmpty();
                boolean hasWalls = walls > 0;
                if (!hasAbilities && !hasWalls) {
                    // Clear freeze and skip their turn
                    gameStateCache.clearFreeze(game.getId(), nextPlayer);
                    int nextNext = (next + 1) % activePlayers.size();
                    game.setCurrentTurnUserId(activePlayers.get(nextNext));
                    resetTurnTimer(game);
                    gameRepository.saveAndFlush(game);
                }
            }
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
                    resetTurnTimer(game);
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

        Lobby lobby = lobbyRepository.findById(game.getLobbyId()).orElse(null);
        if (lobby != null) {
            lobby.setLobbyStatus(LobbyStatus.FINISHED);
            lobbyRepository.saveAndFlush(lobby);
        }

        // Persist match history and update player statistics
        recordMatchResults(game, winnerId, lobby);

        GameGetDTO dto = buildGameGetDTO(game);
        gameStateCache.evictGame(game.getId());
        chatCache.evictGame(game.getId());
        return dto;
    }

    /**
     * Creates one MatchHistory row per player and awards XP/coins via the leveling
     * system.
     *
     * XP sources:
     * 1) Action XP: moves × 2 + walls × 5 (forfeited players get 0)
     * 2) Result XP:
     * - 2-player: winner +100, loser +30, forfeit = 0
     * - 4-player: 1st +150, 2nd +80, 3rd +40, 4th/forfeit = 0
     *
     * Placement in 4-player games is derived from the elimination order tracked
     * in GameStateCache: first eliminated = 4th place, second eliminated = 3rd,
     * the remaining non-winner = 2nd, winner = 1st.
     *
     * Leveling and coin rewards are handled by LevelingService.
     */
    private void recordMatchResults(Game game, Long winnerId, Lobby lobby) {
        String gameMode = (lobby != null) ? lobby.getGameMode() : "Classic";
        LocalDateTime now = LocalDateTime.now();

        List<Long> playerIds = game.getPlayerIds();
        boolean isFourPlayer = playerIds.size() == 4;

        // Build placement map for 4-player games
        Map<Long, Integer> placements = new HashMap<>();
        if (isFourPlayer) {
            // Winner is always 1st
            placements.put(winnerId, 1);

            // Elimination order: first eliminated = worst placement
            List<Long> eliminated = gameStateCache.getEliminationOrder(game.getId());

            // In a 4-player game, 2 players get eliminated (forfeits/disconnects)
            // and the last remaining player wins. The 2nd-place player is whoever
            // was still active (not eliminated and not the winner).
            for (Long pid : playerIds) {
                if (placements.containsKey(pid))
                    continue;
                int elimIndex = eliminated.indexOf(pid);
                if (elimIndex == -1) {
                    // Not eliminated and not winner — this is 2nd place
                    placements.put(pid, 2);
                }
            }

            // Eliminated players: first eliminated = last place
            for (int i = 0; i < eliminated.size(); i++) {
                Long elimPlayer = eliminated.get(i);
                if (!placements.containsKey(elimPlayer)) {
                    // First eliminated = 4th (in 4-player), second = 3rd
                    int placement = 4 - i;
                    if (placement < 2)
                        placement = 2; // safety
                    placements.put(elimPlayer, placement);
                }
            }

            // Fill any remaining unplaced players as last place (safety)
            for (Long pid : playerIds) {
                placements.putIfAbsent(pid, 4);
            }
        }

        // Determine who forfeited (= in elimination order = they didn't finish the game
        // naturally)
        List<Long> eliminated = gameStateCache.getEliminationOrder(game.getId());

        for (Long playerId : playerIds) {
            boolean won = playerId.equals(winnerId);
            boolean forfeited = eliminated.contains(playerId);

            // Collect opponent usernames
            String opponentUsernames = playerIds.stream()
                    .filter(pid -> !pid.equals(playerId))
                    .map(pid -> {
                        User u = userRepository.findById(pid).orElse(null);
                        return u != null ? u.getUsername() : "Unknown";
                    })
                    .collect(Collectors.joining(", "));

            // Persist match history row
            MatchHistory record = new MatchHistory();
            record.setUserId(playerId);
            record.setGameId(game.getId());
            record.setOpponentUsernames(opponentUsernames);
            record.setGameMode(gameMode);
            record.setWon(won);
            record.setPlayedAt(now);

            // Calculate XP
            int moveCount = gameStateCache.getPlayerMoveCount(game.getId(), playerId);
            int wallCount = gameStateCache.getPlayerWallCount(game.getId(), playerId);
            int actionXp = levelingService.calculateActionXp(moveCount, wallCount, forfeited);

            int resultXp;
            if (isFourPlayer) {
                int placement = placements.getOrDefault(playerId, 4);
                resultXp = levelingService.calculateResultXp4Player(placement, forfeited);
            } else {
                resultXp = levelingService.calculateResultXp2Player(won, forfeited);
            }

            int totalXp = actionXp + resultXp;
            record.setXpEarned(totalXp);
            matchHistoryRepository.save(record);

            // Update player XP, level, coins via leveling service
            User player = userRepository.findById(playerId).orElse(null);
            if (player != null) {
                // Score system (unchanged from before)
                if (won) {
                    player.setScore(player.getScore() + 100);
                } else {
                    player.setScore(player.getScore() - 100);
                    if (player.getScore() < 0) {
                        player.setScore(0);
                    }
                }

                // Award XP and handle leveling + coin rewards
                levelingService.awardXp(player, totalXp);

                // Update stats and check achievements
                int wallsPlaced = (int) gameStateCache.getWalls(game.getId()).stream()
                        .filter(w -> playerId.equals(w.getUserId())).count();
                int moves = gameStateCache.getTurnCounter(game.getId()) / playerIds.size();
                userService.updateGameStats(playerId, won);
                userService.checkAndAwardAchievements(playerId, won, moves, wallsPlaced, isFourPlayer);
            }
        }

        matchHistoryRepository.flush();
        userRepository.flush();
    }

    // ─────────────────────────────────────────────────────────────
    // Timer
    // ─────────────────────────────────────────────────────────────
    public void resetTurnTimer(Game game) {
        long now = System.currentTimeMillis();

        if (game.getTurnTimeLimitSeconds() <= 0) {
            game.setTurnTimeLimitSeconds(DEFAULT_TURN_TIME_LIMIT_SECONDS);
        }

        game.setTurnDeadlineMillis(now + game.getTurnTimeLimitSeconds() * 1000L);
    }

    public boolean isTurnExpired(Game game) {
        if (game.getGameStatus() != GameStatus.RUNNING) {
            return false;
        }

        Long deadline = game.getTurnDeadlineMillis();
        return deadline != null && System.currentTimeMillis() >= deadline;
    }

    public GameGetDTO skipTurnByTimeout(Game game) {
        if (game.getGameStatus() == GameStatus.ENDED) {
            return buildGameGetDTO(game);
        }

        advanceTurn(game);

        return buildGameGetDTO(game);
    }

    public GameGetDTO enforceTimeoutIfExpired(Game game) {
        if (isTurnExpired(game)) {
            return skipTurnByTimeout(game);
        }

        return null;    
    }
}