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

/**
 * Handles all chaos-mode ability card logic.
 *
 * Responsibilities:
 *   - Drawing cards from the deck into a player's inventory
 *   - Validating and dispatching ability use to per-ability private methods
 *   - Mutating GameStateCache for positional effects (Fireball, Earthquake, Poison)
 *   - Setting flags in GameStateCache for turn-modifying effects (Freeze, +2 Walls, 2 Moves)
 *   - Removing the used card from the player's inventory
 *
 * Turn flow contract (what AbilityService guarantees vs. what MoveService handles):
 *   - If an ability ENDS the turn → AbilityService calls gameService.advanceTurn()
 *   - If an ability GRANTS an extra action → AbilityService sets a flag in GameStateCache;
 *     MoveService reads the flag on the next move/wall/ability to skip advanceTurn()
 *
 * Abilities that end turn:   FIREBALL, EARTHQUAKE, POISON
 * Abilities that grant extra action: FREEZE, PLUS_TWO_WALLS, TWO_MOVES
 */
@Service
@Transactional
public class AbilityService {

    private static final int INTERNAL_SIZE = 17;
    private static final int WALL_CAP = 12;

    private static final Object[][] CARD_WEIGHTS = { // weighted probabilities 
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
        if(!game.isChaosMode()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ability cards are only available in chaos mode");
        }
        if (!gameStateCache.hasPendingCardDraw(gameId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No card draw available right now");
        }
        AbilityType card = rollWeightedCard();
        gameStateCache.grantCard(gameId, user.getId(), card); // give card to inventory
        return gameService.buildGameGetDTO(game);
    }

    // private helper method used by drawCard to pick a random card
    private AbilityType rollWeightedCard() {
        int totalWeight = 0;
        for(Object[] entry : CARD_WEIGHTS) {
            totalWeight += (int) entry[1]; // total sum weights
        }
        int roll = random.nextInt(totalWeight); // get random int (0-99)
        for(Object[] entry: CARD_WEIGHTS) {
            roll -= (int) entry[1]; // remove weight from roll
            if (roll < 0) { // if roll becomes negative, we take this ability card
                return (AbilityType) entry[0];
            }
        }
        // Fallback – should never be reached if weights sum correctly
        return (AbilityType) CARD_WEIGHTS[0][0];
    }

    public GameGetDTO useAbility(Long gameId, AbilityPostDTO dto, String token) {  // returns updated game state
        User user = requireUser(token);
        Long userId = user.getId();
        Game game = requireGame(gameId);
        requireTurnOrBonusAction(game, userId);
        requireCardInInventory(gameId, userId, dto.getAbilityType());

        switch (dto.getAbilityType()) { // check for each ability what we need to do

            case FIREBALL:
                requireTargetCoords(dto);
                applyFireball(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.FIREBALL);
                gameStateCache.clearBonusAction(gameId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case EARTHQUAKE:
                requireTargetCoords(dto);
                applyEarthquake(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.EARTHQUAKE);
                gameStateCache.clearBonusAction(gameId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case POISON:
                requireTargetCoords(dto);
                applyPoison(gameId, dto.getTargetRow(), dto.getTargetCol());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.POISON);
                gameStateCache.clearBonusAction(gameId);
                gameStateCache.incrementTurnCounter(gameId, game.getPlayerIds());
                gameStateCache.tickPoisonZones(gameId);
                gameService.advanceTurn(game);
                break;

            case FREEZE:
                requireTargetUser(dto);
                applyFreeze(gameId, userId, dto.getTargetUserId());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.FREEZE);
                // does NOT advance turn — grants bonus action instead
                break;
            case PLUS_TWO_WALLS:
                applyPlusTwoWalls(gameId, userId, game.getWallsPerPlayer());
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.PLUS_TWO_WALLS);
                // does NOT advance turn — grants bonus action instead
                break;
            case TWO_MOVES:
                applyTwoMoves(gameId, userId);
                gameStateCache.removeCardFromInventory(gameId, userId, AbilityType.TWO_MOVES);
                // does NOT advance turn — grants bonus action instead
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
 
        // 3×3 logical area → internal span of ±2 from the centre
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
            // Phase 1: 50 % destruction
            if (random.nextBoolean()) {
                gameStateCache.removeWall(gameId, wall.getRow(), wall.getCol(), wall.getOrientation());
                continue;
            }
 
            // Phase 2: surviving wall – 50 % chance to flip orientation (≈ 25 % overall)
            if (random.nextBoolean()) {
                WallOrientation flipped = wall.getOrientation() == WallOrientation.HORIZONTAL
                    ? WallOrientation.VERTICAL
                    : WallOrientation.HORIZONTAL;
 
                int r = wall.getRow();
                int c = wall.getCol();
 
                // Flipped wall must stay within the 17×17 grid
                boolean inBounds = flipped == WallOrientation.HORIZONTAL
                    ? (c - 1 >= 0 && c + 1 < INTERNAL_SIZE)
                    : (r - 1 >= 0 && r + 1 < INTERNAL_SIZE);
 
                if (inBounds) {
                    boolean[][] grid = gameStateCache.getWallGrid(gameId);
                    if (!wouldOverlap(grid, r, c, flipped, r, c, wall.getOrientation())) {
                        gameStateCache.removeWall(gameId, r, c, wall.getOrientation());
                        gameStateCache.placeWall(gameId, r, c, flipped, wall.getUserId());
                    }
                    // If it would overlap, wall stays unchanged – already in place
                }
            }
            // else: wall survives unchanged
        }
    }

    private void applyFreeze(Long gameId, Long casterUserId, Long targetUserId) {
        if (casterUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot freeze yourself");
        }
        if (!gameStateCache.getPlayers(gameId).contains(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not in the game");
        }
        if (gameStateCache.isFrozen(gameId, targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is already frozen");
        }
        gameStateCache.freezePlayer(gameId, targetUserId);
        gameStateCache.setBonusAction(gameId, casterUserId, 1);
    }

    private void applyPoison(Long gameId, int targetRow, int targetCol) {
        // Convert to internal top-left (even,even)
        int internalRow = targetRow * 2;
        int internalCol = targetCol * 2;
        validateBoardCoord(internalRow, internalCol, "POISON");
        gameStateCache.addPoisonZone(gameId, internalRow, internalCol);
    }

    private void applyPlusTwoWalls(Long gameId, Long userId, int wallsPerPlayer) {
        int currentExtra = gameStateCache.getExtraWalls(gameId, userId);
        int newExtra     = currentExtra + 2;
 
        // Cap: base + extra must not exceed WALL_CAP
        if (wallsPerPlayer + newExtra > WALL_CAP) {
            newExtra = WALL_CAP - wallsPerPlayer;
        }
 
        int gain = newExtra - currentExtra;
        if (gain > 0) {
            gameStateCache.addExtraWalls(gameId, userId, gain);
        }
        gameStateCache.setBonusAction(gameId, userId);
    }

    private void applyTwoMoves(Long gameId, Long userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // Guards for validation logic
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your turn");
        } 
    }

    private void requireCardInInventory(Long gameId, Long userId, AbilityType type) {
        if (!gameStateCache.getInventory(gameId, userId).contains(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Card not in inventory");
        }
    }

    private void requireTargetCoords(AbilityPostDTO dto) {
        if (dto.getTargetRow() == null || dto.getTargetCol() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target coordinates required");
        }
    }
 
    private void requireTargetUser(AbilityPostDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user required");
        }
    }
 
    private void validateBoardCoord(int row, int col, String ability) {
        switch (ability) {
            case "FIREBALL":
                    if (row < 0  || col < 0 ) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Fireball area of effect out of bounds");
                    }
                    if (row + 2 >= INTERNAL_SIZE || col + 2 >= INTERNAL_SIZE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Fireball area of effect out of bounds");
                    }
                    break;

            case "POISON":
                    if (row < 0  || col < 0 ) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Poison area of effect out of bounds");
                    }
                    if (row + 2 >= INTERNAL_SIZE || col + 2 >= INTERNAL_SIZE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Poison area of effect out of bounds");
                    }
                    break;

            case "EARTHQUAKE":
                    if (row - 2 < 0  || col - 2  < 0 ) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Earthquake area of effect out of bounds");
                    }
                    if (row + 2 >= INTERNAL_SIZE || col + 2 >= INTERNAL_SIZE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Earthquake area of effect out of bounds");
                    }
                    break;
        
            default:
                break;
        } 
    }

    // Helper methods
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
