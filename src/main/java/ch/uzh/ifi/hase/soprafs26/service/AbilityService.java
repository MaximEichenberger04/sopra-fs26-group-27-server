package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.AbilityPostDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AbilityService {

    private static final int INTERNAL_SIZE = 17;
    private static final int WALL_CAP = 12;

    private static final Object[][] CARD_WEIGHTS = {
        { AbilityType.PLUS_TWO_WALLS, 20 },
        { AbilityType.TWO_MOVES,      20 },
        { AbilityType.FIREBALL,       15 },
        { AbilityType.FREEZE,         15 },
        { AbilityType.EARTHQUAKE,     15 },
        { AbilityType.POISON,         15 },
    };

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameService gameService;
    private final GameStateCache gameStateCache;
    private final Random random = new Random();

    public AbilityService(
            @Qualifier("gameRepository") GameRepository gameRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            GameService gameService,
            GameStateCache gameStateCache) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.gameService    = gameService;
        this.gameStateCache = gameStateCache;
    }

    public GameGetDTO drawCard(Long gameId, String token) {
        User user = requireUser(token);
        Game game = requireGame(gameId);
        if (!game.isChaosMode()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ability cards are only available in chaos mode");
        }
        if (!gameStateCache.hasPendingCardDraw(gameId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No card draw available right now");
        }
        AbilityType card = rollWeightedCard();
        gameStateCache.grantCard(gameId, user.getId(), card);
        return gameService.buildGameGetDTO(game);
    }

    private AbilityType rollWeightedCard() {
        int totalWeight = 0;
        for (Object[] entry : CARD_WEIGHTS) {
            totalWeight += (int) entry[1];
        }
        int roll = random.nextInt(totalWeight);
        for (Object[] entry : CARD_WEIGHTS) {
            roll -= (int) entry[1];
            if (roll < 0) {
                return (AbilityType) entry[0];
            }
        }
        return (AbilityType) CARD_WEIGHTS[0][0];
    }

    public GameGetDTO useAbility(Long gameId, AbilityPostDTO dto, String token) {
        User user = requireUser(token);
        Long userId = user.getId();
        Game game = requireGame(gameId);
        requireTurnOrBonusAction(game, userId);
        requireCardInInventory(gameId, userId, dto.getAbilityType());
        // If in bonus mode, consume one bonus action before resolving the card
        if (gameStateCache.hasBonusAction(gameId, userId)) {
            gameStateCache.consumeBonusAction(gameId, userId);
        }

        switch (dto.getAbilityType()) {

            case FIREBALL:
                requireTargetCoords(dto);
                applyFireball(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.FIREBALL);
                gameStateCache.clearBonusAction(gameId, userId);
                gameStateCache.clearFreeze(gameId, userId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case EARTHQUAKE:
                requireTargetCoords(dto);
                applyEarthquake(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.EARTHQUAKE);
                gameStateCache.clearBonusAction(gameId, userId);
                gameStateCache.clearFreeze(gameId, userId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case POISON:
                requireTargetCoords(dto);
                applyPoison(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.POISON);
                gameStateCache.clearBonusAction(gameId, userId);
                gameStateCache.clearFreeze(gameId, userId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case FREEZE:
                requireTargetUser(dto);
                applyFreeze(gameId, userId, dto.getTargetUserId());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.FREEZE);
                gameStateCache.setBonusAction(gameId, userId, 1);  // 1 bonus: can do 1 more action
                break;

            case PLUS_TWO_WALLS:
                applyPlusTwoWalls(gameId, userId, game.getWallsPerPlayer());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.PLUS_TWO_WALLS);
                gameStateCache.setBonusAction(gameId, userId, 1);  // 1 bonus: can do 1 more action
                break;

            case TWO_MOVES:
                applyTwoMoves(gameId, userId);
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.TWO_MOVES);
                break;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown ability type: " + dto.getAbilityType());
        }
        return gameService.buildGameGetDTO(game, userId);
    }

    private void applyFireball(Long gameId, int targetRow, int targetCol) {
        int internalRow = targetRow * 2;
        int internalCol = targetCol * 2;
        validateBoardCoord(internalRow, internalCol, "FIREBALL");

        int minR = internalRow - 1;
        int maxR = internalRow + 3;
        int minC = internalCol - 1;
        int maxC = internalCol + 3;

        List<Wall> snapshot = new ArrayList<>(gameStateCache.getWalls(gameId));
        List<Wall> toRemove = new ArrayList<>();
        for (Wall wall : snapshot) {
            if (wallTouchesRegion(wall, minR, maxR, minC, maxC)) {
                toRemove.add(wall);
            }
        }
        for (Wall wall : toRemove) {
            gameStateCache.removeWall(gameId, wall.getRow(), wall.getCol(), wall.getOrientation());
        }
    }

    private void applyEarthquake(Long gameId, int targetRow, int targetCol) {
        int internalRow = targetRow * 2;
        int internalCol = targetCol * 2;
        validateBoardCoord(internalRow, internalCol, "EARTHQUAKE");

        int minR = internalRow - 3;
        int maxR = internalRow + 3;
        int minC = internalCol - 3;
        int maxC = internalCol + 3;

        List<Wall> snapshot = new ArrayList<>(gameStateCache.getWalls(gameId));
        List<Wall> inRegion = new ArrayList<>();
        for (Wall wall : snapshot) {
            if (wallTouchesRegion(wall, minR, maxR, minC, maxC)) {
                inRegion.add(wall);
            }
        }

        for (Wall wall : inRegion) {
            if (random.nextBoolean()) {
                gameStateCache.removeWall(gameId, wall.getRow(), wall.getCol(), wall.getOrientation());
                continue;
            }
            if (random.nextBoolean()) {
                WallOrientation flipped = wall.getOrientation() == WallOrientation.HORIZONTAL
                    ? WallOrientation.VERTICAL
                    : WallOrientation.HORIZONTAL;

                int r = wall.getRow();
                int c = wall.getCol();

                boolean inBounds = flipped == WallOrientation.HORIZONTAL
                    ? (c - 1 >= 0 && c + 1 < INTERNAL_SIZE)
                    : (r - 1 >= 0 && r + 1 < INTERNAL_SIZE);

                if (inBounds) {
                    boolean[][] grid = gameStateCache.getWallGrid(gameId);
                    if (!wouldOverlap(grid, r, c, flipped, r, c, wall.getOrientation())) {
                        gameStateCache.removeWall(gameId, r, c, wall.getOrientation());
                        gameStateCache.placeWall(gameId, r, c, flipped, wall.getUserId());
                    }
                }
            }
        }
    }

    private void applyFreeze(Long gameId, Long casterUserId, Long targetUserId) {
        if (casterUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot freeze yourself. Choose an opponent to freeze.");
        }
        if (!gameStateCache.getPlayers(gameId).contains(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target player is not in this game.");
        }
        if (gameStateCache.isFrozen(gameId, targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That player is already frozen — you cannot freeze them again.");
        }
        gameStateCache.freezePlayer(gameId, targetUserId);
        // Playing FREEZE ends your turn — no bonus action granted
    }

    private void applyPoison(Long gameId, int targetRow, int targetCol) {
        int internalRow = targetRow * 2;
        int internalCol = targetCol * 2;
        validateBoardCoord(internalRow, internalCol, "POISON");
        gameStateCache.addPoisonZone(gameId, internalRow, internalCol);
    }

    private void applyPlusTwoWalls(Long gameId, Long userId, int wallsPerPlayer) {
        int consumed = gameStateCache.getPermanentlyConsumedWalls(gameId, userId);
        int extra = gameStateCache.getExtraWalls(gameId, userId);
        int remaining = wallsPerPlayer + extra - consumed;
        int maxRemaining = wallsPerPlayer + 2;
        if (remaining >= maxRemaining) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot use +2 Walls: you already have the maximum of " + maxRemaining + " walls remaining.");
        }
        int gain = Math.min(2, maxRemaining - remaining);
        gameStateCache.addExtraWalls(gameId, userId, gain);
    }


    private void applyTwoMoves(Long gameId, Long userId) {
        // Grants 2 bonus actions (card play is free, then 2 more actions = 2 moves total)
        gameStateCache.setBonusAction(gameId, userId, 2);
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private User requireUser(String token) {
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        return user;
    }

    private Game requireGame(Long gameId) {
        return gameRepository.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
    }

    private void requireTurnOrBonusAction(Game game, Long userId) {
        if (game.getGameStatus() != GameStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game is not running");
        }
        if (!game.isChaosMode()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can not use abilities in non-chaos mode");
        }
        if (!game.getCurrentTurnUserId().equals(userId) && !gameStateCache.hasBonusAction(game.getId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "It is not your turn. Wait for your turn to use ability cards.");
        }
    }

    private void requireCardInInventory(Long gameId, Long userId, AbilityType type) {
        if (!gameStateCache.getInventory(gameId, userId).contains(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have that ability card in your inventory.");
        }
    }

    private void requireTargetCoords(AbilityPostDTO dto) {
        if (dto.getTargetRow() == null || dto.getTargetCol() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This ability requires a target location on the board.");
        }
    }

    private void requireTargetUser(AbilityPostDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FREEZE requires a target player. Click on an opponent's pawn.");
        }
    }

    private void validateBoardCoord(int row, int col, String ability) {
        if (row < 0 || row >= INTERNAL_SIZE || col < 0 || col >= INTERNAL_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                ability + " target is outside the board.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean wallTouchesRegion(Wall wall, int minR, int maxR, int minC, int maxC) {
        for (int[] segment : wallSegments(wall)) {
            int row = segment[0];
            int col = segment[1];
            if (row >= minR && row <= maxR && col >= minC && col <= maxC) {
                return true;
            }
        }
        return false;
    }

    private List<int[]> wallSegments(Wall wall) {
        List<int[]> segments = new ArrayList<>();
        int row = wall.getRow();
        int col = wall.getCol();
        if (wall.getOrientation() == WallOrientation.HORIZONTAL) {
            segments.add(new int[]{row, col - 1});
            segments.add(new int[]{row, col});
            segments.add(new int[]{row, col + 1});
        } else {
            segments.add(new int[]{row - 1, col});
            segments.add(new int[]{row, col});
            segments.add(new int[]{row + 1, col});
        }
        return segments;
    }

    private boolean wouldOverlap(boolean[][] grid,
                                  int newRow, int newCol, WallOrientation newOrientation,
                                  int oldRow, int oldCol, WallOrientation oldOrientation) {
        boolean[][] copy = copyGrid(grid);
        clearWallInGrid(copy, oldRow, oldCol, oldOrientation);

        if (newOrientation == WallOrientation.HORIZONTAL) {
            return copy[newRow][newCol - 1] || copy[newRow][newCol] || copy[newRow][newCol + 1];
        } else {
            return copy[newRow - 1][newCol] || copy[newRow][newCol] || copy[newRow + 1][newCol];
        }
    }

    private boolean[][] copyGrid(boolean[][] grid) {
        boolean[][] copy = new boolean[INTERNAL_SIZE][INTERNAL_SIZE];
        for (int i = 0; i < INTERNAL_SIZE; i++) {
            System.arraycopy(grid[i], 0, copy[i], 0, INTERNAL_SIZE);
        }
        return copy;
    }

    private void clearWallInGrid(boolean[][] grid, int row, int col, WallOrientation orientation) {
        if (orientation == WallOrientation.HORIZONTAL) {
            grid[row][col - 1] = false;
            grid[row][col]     = false;
            grid[row][col + 1] = false;
        } else {
            grid[row - 1][col] = false;
            grid[row][col]     = false;
            grid[row + 1][col] = false;
        }
    }
}