package ch.uzh.ifi.hase.soprafs26.service;
 
import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.PoisonZone;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
 
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for all active game state (walls and pawns).
 * JPA is not used for game state, this cache is the single source of truth
 * during a game. Cleared when the game ends.
 *
 * Per game, holds:
 *   boolean[17][17] wallGrid, used for wall lookups for BFS and conflict checks
 *   List<Wall>       walls, an ordered list sent to the frontend for rendering
 *   List<Pawn>       pawns, current pawn positions (one per player)
 *
 * Wall rules (center at odd, odd in 17×17 grid):
 *   HORIZONTAL: marks (row, col-1), (row, col), (row, col+1) in grid
 *   VERTICAL:   marks (row-1, col), (row, col), (row+1, col) in grid
 *
 * Starting positions (17×17 internal grid):
 *   Player index 0: row=16, col=8  →  goal row = 0   (starts south, moves north)
 *   Player index 1: row=0,  col=8  →  goal row = 16  (starts north, moves south)
 *
 * Lifecycle:
 *   initGame, called by GameService.createGameFromLobby
 *   placeWall, called by MoveService.applyWallPlacement
 *   movePawn, called by MoveService.processMove
 *   evictGame, called by GameService.forfeitGame / win handling
 */
@Component
public class GameStateCache {

    private static final int INTERNAL_SIZE = 17;
    private static final int MAX_CARDS_HELD = 3;
    private static final int TURNS_PER_DRAW_CYCLE = 6; // 2 players × 3 rounds = 6 turns between each draw opportunity.
    private static final int POISON_INITIAL_ROUNDS = 4;

    private static final int[][] START_POSITIONS = {
        {16, 8}, // player 0, starts bottom center, moves north
        {0, 8},   // player 1, starts top center, moves south
        {8, 16}, // player 2 starts on the right, moves left
        {8, 0}  //player 3 starts on left, moves right
    };

    // Classic Gamemode
    private final Map<Long, boolean[][]> wallGrids = new ConcurrentHashMap<>();
    private final Map<Long, List<Wall>>  walls     = new ConcurrentHashMap<>();
    private final Map<Long, List<Pawn>>  pawns     = new ConcurrentHashMap<>();

    // Chaos Gamemode (additional)
    private final Map<Long, Map<Long, List<AbilityType>>> playerInventories = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>>                    pendingCardDraw   = new ConcurrentHashMap<>();
    private final Map<Long, Integer>                      turnCounter       = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>>                    frozenPlayers     = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>>           bonusActions      = new ConcurrentHashMap<>();
    private final Map<Long, List<PoisonZone>>             poisonZones       = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>>           extraWalls        = new ConcurrentHashMap<>();
    /**
     * Initialises an empty wall grid and places pawns at their
     * starting positions for the given player list.
     */
    public void initGame(Long gameId, List<Long> playerIds, boolean isChaosMode) {
        if (playerIds.size() > START_POSITIONS.length){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Too many players. Maximum supported is " + START_POSITIONS.length);
        }
        wallGrids.put(gameId, new boolean[INTERNAL_SIZE][INTERNAL_SIZE]);
        walls.put(gameId, new ArrayList<>());

        // Creates a pawn for each player, assigns an ID, user, and starting position, and stores all pawns for this game
        List<Pawn> pawnList = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++){
            Pawn pawn = new Pawn();
            pawn.setId((long) (i+1));
            pawn.setUserId(playerIds.get(i));
            pawn.setRow(START_POSITIONS[i][0]);
            pawn.setCol(START_POSITIONS[i][1]);
            pawnList.add(pawn);
        }
        pawns.put(gameId, pawnList);

        if (!isChaosMode) return;
        // Per-player structures for Chaos mode
        Map<Long, List<AbilityType>> inventories = new HashMap<>();
        Map<Long, Integer>           wallBonuses = new HashMap<>();
        for (Long playerId : playerIds) {
            inventories.put(playerId, new ArrayList<>());
            wallBonuses.put(playerId, 0);
        }
        playerInventories.put(gameId, inventories);
        extraWalls.put(gameId, wallBonuses);
        frozenPlayers.put(gameId, new HashSet<>());
        poisonZones.put(gameId, new ArrayList<>());
        pendingCardDraw.put(gameId, new HashSet<>());
        turnCounter.put(gameId, 0);
    }


    /**
     * Marks the three cells occupied by the wall in the grid and appends
     * the wall to the render list. Must be called after turn validation.
     */
    public void placeWall(Long gameId, int row, int col, WallOrientation orientation, Long userId){
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for game " + gameId);
        }
        if (orientation == WallOrientation.HORIZONTAL){
            grid[row][col -1] = true;
            grid[row][col   ] = true;
            grid[row][col +1] = true;
        } else { // VERTICAL
            grid[row - 1][col] = true;
            grid[row    ][col] = true;
            grid[row + 1][col] = true;   
        }
        
        // create a new wall
        Wall wall = new Wall();
        wall.setId((long)(walls.get(gameId).size() + 1));
        wall.setUserId(userId);
        wall.setRow(row);
        wall.setCol(col);
        wall.setOrientation(orientation);

        // add the new wall to the walls list of the game
        walls.get(gameId).add(wall);
    }

    public void removeWall(Long gameId, int row, int col, WallOrientation orientation) {
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for game " + gameId);
        }
        if (orientation == WallOrientation.HORIZONTAL){
            grid[row][col -1] = false;
            grid[row][col   ] = false;
            grid[row][col +1] = false;
        } else { // VERTICAL
            grid[row - 1][col] = false;
            grid[row    ][col] = false;
            grid[row + 1][col] = false;   
        }
        
        // remove the wall from the walls list of the game
        List<Wall> wallList = walls.get(gameId);
        wallList.removeIf(w -> w.getRow() == row && w.getCol() == col && w.getOrientation() == orientation);
    }

    /**
     * Updates the pawn position for the given player.
     * Must be called after move validation.
     */
    public void movePawn(Long gameId, Long userId, int row, int col) {
        Pawn pawn = getPawn(gameId, userId);
        if (pawn == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pawn not found for the user " + userId);
        }
        pawn.setRow(row);
        pawn.setCol(col);
    }

    /**
     * Returns the wall occupancy grid for.
     * grid[r][c] == true means that cell is blocked by a wall.
     */
    public boolean[][] getWallGrid(Long gameId) {
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for the game " + gameId);
        }
        return grid;  
    }

    // Returns the ordered wall list for frontend rendering.
    public List<Wall> getWalls(Long gameId) {
        List<Wall> w = walls.get(gameId);
        // Returns an empty list if no walls exist for this game to avoid null checks
        if (w == null) {
            return new  ArrayList<>();
        }
        return w;
    }

    // Returns all pawns for the given game.
    public List<Pawn> getPawns(Long gameId) {
        List<Pawn> p = pawns.get(gameId);
        // Returns an empty list if no walls exist for this game to avoid null checks
        if (p == null) {
            return new ArrayList<>();
        }
        return p;
    }

    // Returns the pawn belonging to the given player, or null if not found. 
    public Pawn getPawn(Long gameId, Long userId) {
        List<Pawn> pawnList = pawns.get(gameId);
        if (pawnList == null) return null;

        for (Pawn p : pawnList){
            if (p.getUserId().equals(userId)){
                return p;
            }
        }
        return null; // pawn was not found
    }

    // Returns the list of player IDs registered for this game.
    public List<Long> getPlayers(Long gameId) {
        List<Pawn> pawnList = pawns.get(gameId);
        if (pawnList == null) return Collections.emptyList();
        List<Long> playerIds = new ArrayList<>();
        for (Pawn p : pawnList) playerIds.add(p.getUserId());
        return Collections.unmodifiableList(playerIds);
    }

    // Removes all state for a finished game to free memory. 
    public void evictGame(Long gameId) {
        wallGrids.remove(gameId);
        walls.remove(gameId);
        pawns.remove(gameId);
        // Chaos mode
        turnCounter.remove(gameId);
        pendingCardDraw.remove(gameId);
        playerInventories.remove(gameId);
        frozenPlayers.remove(gameId);
        bonusActions.remove(gameId);
        poisonZones.remove(gameId);
        extraWalls.remove(gameId);
    }

    // Player card inventory methods
    public void incrementTurnCounter(Long gameId, List<Long> playerIds) {
        Integer current = turnCounter.get(gameId);
        if (current == null) return; // not a chaos game
 
        int next = current + 1;
        turnCounter.put(gameId, next);
 
        // Every TURNS_PER_DRAW_CYCLE turns, open the draw window for eligible players
        if (next % TURNS_PER_DRAW_CYCLE == 0) {
            Set<Long> pending = pendingCardDraw.get(gameId);
            Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
            for (Long playerId : playerIds) {
                List<AbilityType> hand = inventories != null
                    ? inventories.getOrDefault(playerId, Collections.emptyList())
                    : Collections.emptyList();
                // Only offer a draw if there is room in the inventory
                if (hand.size() < MAX_CARDS_HELD) {
                    pending.add(playerId);
                }
            }
        }
    }

    public boolean hasPendingCardDraw(Long gameId, Long userId) {
        Set<Long> pending = pendingCardDraw.get(gameId);
        return pending != null && pending.contains(userId);
    }

    public int getTurnCounter(Long gameId) {
        Integer count = turnCounter.get(gameId);
        return count != null ? count : 0;
    }

    public void grantCard(Long gameId, Long userId, AbilityType card) {
        Map<Long, List<AbilityType>> inventories = requireInventories(gameId);
        List<AbilityType> hand = inventories.computeIfAbsent(userId, k -> new ArrayList<>());
 
        if (hand.size() >= MAX_CARDS_HELD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Inventory is full (" + MAX_CARDS_HELD + " cards max)");
        }
        hand.add(card);
        Set<Long> pending = pendingCardDraw.get(gameId);
        if (pending != null) pending.remove(userId);
    }

    public List<AbilityType> getInventory(Long gameId, Long userId) {
        Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
        if (inventories == null) return Collections.emptyList();
        List<AbilityType> hand = inventories.get(userId);
        return hand != null ? Collections.unmodifiableList(hand) : Collections.emptyList();
    }
    
    public Map<Long, List<AbilityType>> getAllInventories(Long gameId) {
        Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
        return inventories != null ? Collections.unmodifiableMap(inventories) : Collections.emptyMap();
    }

    public void removeCardFromInventory(Long gameId, Long userId, AbilityType type) {
        Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
        List<AbilityType> hand = inventories.getOrDefault(userId, Collections.emptyList());

        if (!hand.remove(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Card " + type + " not found in your inventory");
        }
    }

    // Freeze Ability methods
    public void freezePlayer(Long gameId, Long userId) {
        frozenPlayers.get(gameId).add(userId);
    }
 
    public boolean isFrozen(Long gameId, Long userId) {
        return frozenPlayers.getOrDefault(gameId, Collections.emptySet()).contains(userId);
    }
 
    public void clearFreeze(Long gameId, Long userId) {
        Set<Long> frozen = frozenPlayers.get(gameId);
        if (frozen != null) frozen.remove(userId);
    }

    // Bonus action methods (used by freeze, +2 Walls, 2 Moves)
    public void setBonusAction(Long gameId, Long userId, int count) {
        bonusActions
            .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
            .merge(userId, count, Integer::sum); 
    }

    public boolean hasBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap == null) return false;
        Integer remaining = gameMap.get(userId);
        return remaining != null && remaining > 0;
    }

    // Consumes one bonus action, removes entry when exhausted
    public void consumeBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap == null) return;
        gameMap.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1);
    }

    public void clearBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap != null) gameMap.remove(userId);
    }

    // Poison Ability methods
    public void addPoisonZone(Long gameId, int topLeftRow, int topLeftCol) {
        List<PoisonZone> zones = requirePoisonZones(gameId);

        // Check no pawn is already on any of the 4 cells
        List<Pawn> pawnList = getPawns(gameId);
        for (int dr = 0; dr <= 2; dr += 2) {
            for (int dc = 0; dc <= 2; dc += 2) {
                int r = topLeftRow + dr;
                int c = topLeftCol + dc;
                for (Pawn p : pawnList) {
                    if (p.getRow() == r && p.getCol() == c) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot place poison zone on a cell occupied by a pawn");
                    }
                }
            }
        }

        PoisonZone zone = new PoisonZone();
        zone.setId((long) (zones.size() + 1));
        zone.setTopLeftRow(topLeftRow);
        zone.setTopLeftCol(topLeftCol);
        zone.setRoundsRemaining(POISON_INITIAL_ROUNDS);
        zones.add(zone);
    }
 
    public List<PoisonZone> getPoisonZones(Long gameId) {
        List<PoisonZone> zones = poisonZones.get(gameId);
        return zones != null ? Collections.unmodifiableList(zones) : Collections.emptyList();
    }
 
    public void tickPoisonZones(Long gameId) {
        List<PoisonZone> zones = poisonZones.get(gameId);
        if (zones == null) return;
        zones.forEach(z -> z.setRoundsRemaining(z.getRoundsRemaining() - 1));
        zones.removeIf(z -> z.getRoundsRemaining() <= 0);
    }

    public boolean isPoisoned(Long gameId, int row, int col) {
        List<PoisonZone> zones = poisonZones.get(gameId);
        if (zones == null) return false;
        for (PoisonZone z : zones) {
            // Zone covers topLeftRow, topLeftRow+2 and topLeftCol, topLeftCol+2
            boolean rowInZone = (row == z.getTopLeftRow() || row == z.getTopLeftRow() + 2);
            boolean colInZone = (col == z.getTopLeftCol() || col == z.getTopLeftCol() + 2);
            if (rowInZone && colInZone) return true;
        }
        return false;
    }

    // +2 Walls methods
    public void addExtraWalls(Long gameId, Long userId, int count) {
        Map<Long, Integer> bonuses = extraWalls.get(gameId);
        if (bonuses == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a chaos game");
        }
        int current = bonuses.getOrDefault(userId, 0);
        bonuses.put(userId, current + count);
    }

    // Returns the bonus wall count for this player (0 if none). 
    public int getExtraWalls(Long gameId, Long userId) {
        Map<Long, Integer> bonuses = extraWalls.get(gameId);
        if (bonuses == null) return 0;
        return bonuses.getOrDefault(userId, 0);
    }

    
    // Private helpers

    private Map<Long, List<AbilityType>> requireInventories(Long gameId) {
        Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
        if (inventories == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a chaos game");
        }
        return inventories;
    }

    private List<PoisonZone> requirePoisonZones(Long gameId) {
        List<PoisonZone> zones = poisonZones.get(gameId);
        if (zones == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a chaos game");
        }
        return zones;
    }
}