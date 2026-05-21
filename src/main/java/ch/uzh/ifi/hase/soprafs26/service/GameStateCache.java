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

@Component
public class GameStateCache {

    private static final int INTERNAL_SIZE = 17;
    private static final int MAX_CARDS_HELD = 3;
    private static final int TURNS_PER_DRAW_CYCLE = 6;
    private static final int POISON_INITIAL_ROUNDS = 2;

    private static final int[][] START_POSITIONS = {
            { 16, 8 },
            { 0, 8 },
            { 8, 16 },
            { 8, 0 }
    };

    // Classic Gamemode
    private final Map<Long, boolean[][]> wallGrids = new ConcurrentHashMap<>();
    private final Map<Long, List<Wall>> walls = new ConcurrentHashMap<>();
    private final Map<Long, List<Pawn>> pawns = new ConcurrentHashMap<>();

    // Chaos Gamemode
    private final Map<Long, Map<Long, List<AbilityType>>> playerInventories = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> pendingCardDraw = new ConcurrentHashMap<>();
    private final Map<Long, Integer> turnCounter = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> frozenPlayers = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>> bonusActions = new ConcurrentHashMap<>();
    private final Map<Long, List<PoisonZone>> poisonZones = new ConcurrentHashMap<>();
    private final Map<Long, Integer> playerCount = new ConcurrentHashMap<>();
    private final Map<Long, Integer> poisonTurnTick = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>> extraWalls = new ConcurrentHashMap<>();
    // Tracks walls permanently consumed from a player's budget (even if wall was
    // later destroyed)
    private final Map<Long, Map<Long, Integer>> permanentlyConsumedWalls = new ConcurrentHashMap<>();

    // XP system: per-player action counters
    private final Map<Long, Map<Long, Integer>> playerMoveCount = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Integer>> playerWallCount = new ConcurrentHashMap<>();
    // Tracks elimination order for 4-player placement (first eliminated = last
    // place)
    private final Map<Long, List<Long>> eliminationOrder = new ConcurrentHashMap<>();

    public void initGame(Long gameId, List<Long> playerIds, boolean isChaosMode) {
        if (playerIds.size() > START_POSITIONS.length) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many players. Maximum supported is " + START_POSITIONS.length);
        }
        wallGrids.put(gameId, new boolean[INTERNAL_SIZE][INTERNAL_SIZE]);
        walls.put(gameId, new ArrayList<>());

        List<Pawn> pawnList = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            Pawn pawn = new Pawn();
            pawn.setId((long) (i + 1));
            pawn.setUserId(playerIds.get(i));
            pawn.setRow(START_POSITIONS[i][0]);
            pawn.setCol(START_POSITIONS[i][1]);
            pawnList.add(pawn);
        }
        pawns.put(gameId, pawnList);

        // XP system: init per-player action counters for all game modes
        Map<Long, Integer> moveCounts = new HashMap<>();
        Map<Long, Integer> wallCounts = new HashMap<>();
        for (Long playerId : playerIds) {
            moveCounts.put(playerId, 0);
            wallCounts.put(playerId, 0);
        }
        playerMoveCount.put(gameId, moveCounts);
        playerWallCount.put(gameId, wallCounts);
        eliminationOrder.put(gameId, new ArrayList<>());

        if (!isChaosMode)
            return;

        Map<Long, List<AbilityType>> inventories = new HashMap<>();
        Map<Long, Integer> wallBonuses = new HashMap<>();
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
        playerCount.put(gameId, playerIds.size());
        poisonTurnTick.put(gameId, 0);
    }

    public void placeWall(Long gameId, int row, int col, WallOrientation orientation, Long userId) {
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for game " + gameId);
        }
        if (orientation == WallOrientation.HORIZONTAL) {
            grid[row][col - 1] = true;
            grid[row][col] = true;
            grid[row][col + 1] = true;
        } else {
            grid[row - 1][col] = true;
            grid[row][col] = true;
            grid[row + 1][col] = true;
        }

        Wall wall = new Wall();
        wall.setId((long) (walls.get(gameId).size() + 1));
        wall.setUserId(userId);
        wall.setRow(row);
        wall.setCol(col);
        wall.setOrientation(orientation);
        walls.get(gameId).add(wall);
    }

    public void removeWall(Long gameId, int row, int col, WallOrientation orientation) {
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for game " + gameId);
        }
        if (orientation == WallOrientation.HORIZONTAL) {
            grid[row][col - 1] = false;
            grid[row][col] = false;
            grid[row][col + 1] = false;
        } else {
            grid[row - 1][col] = false;
            grid[row][col] = false;
            grid[row + 1][col] = false;
        }
        List<Wall> wallList = walls.get(gameId);
        wallList.removeIf(w -> w.getRow() == row && w.getCol() == col && w.getOrientation() == orientation);
    }

    public void movePawn(Long gameId, Long userId, int row, int col) {
        Pawn pawn = getPawn(gameId, userId);
        if (pawn == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pawn not found for user " + userId);
        }
        pawn.setRow(row);
        pawn.setCol(col);
    }

    public boolean[][] getWallGrid(Long gameId) {
        boolean[][] grid = wallGrids.get(gameId);
        if (grid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found for game " + gameId);
        }
        return grid;
    }

    public List<Wall> getWalls(Long gameId) {
        List<Wall> w = walls.get(gameId);
        return w != null ? w : new ArrayList<>();
    }

    public List<Pawn> getPawns(Long gameId) {
        List<Pawn> p = pawns.get(gameId);
        return p != null ? p : new ArrayList<>();
    }

    public Pawn getPawn(Long gameId, Long userId) {
        List<Pawn> pawnList = pawns.get(gameId);
        if (pawnList == null)
            return null;
        for (Pawn p : pawnList) {
            if (p.getUserId().equals(userId))
                return p;
        }
        return null;
    }

    public List<Long> getPlayers(Long gameId) {
        List<Pawn> pawnList = pawns.get(gameId);
        if (pawnList == null)
            return Collections.emptyList();
        List<Long> playerIds = new ArrayList<>();
        for (Pawn p : pawnList)
            playerIds.add(p.getUserId());
        return Collections.unmodifiableList(playerIds);
    }

    public void evictGame(Long gameId) {
        wallGrids.remove(gameId);
        walls.remove(gameId);
        pawns.remove(gameId);
        turnCounter.remove(gameId);
        pendingCardDraw.remove(gameId);
        playerInventories.remove(gameId);
        frozenPlayers.remove(gameId);
        bonusActions.remove(gameId);
        poisonZones.remove(gameId);
        playerCount.remove(gameId);
        poisonTurnTick.remove(gameId);
        extraWalls.remove(gameId);
        playerMoveCount.remove(gameId);
        playerWallCount.remove(gameId);
        eliminationOrder.remove(gameId);
    }

    // ── Turn counter & card draw ──────────────────────────────────────────────

    public void incrementTurnCounter(Long gameId, List<Long> playerIds) {
        Integer current = turnCounter.get(gameId);
        if (current == null)
            return;

        int next = current + 1;
        turnCounter.put(gameId, next);

        if (next % TURNS_PER_DRAW_CYCLE == 0) {
            Set<Long> pending = pendingCardDraw.get(gameId);
            Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
            for (Long playerId : playerIds) {
                List<AbilityType> hand = inventories != null
                        ? inventories.getOrDefault(playerId, Collections.emptyList())
                        : Collections.emptyList();
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

    // ── Inventory ─────────────────────────────────────────────────────────────

    public void grantCard(Long gameId, Long userId, AbilityType card) {
        Map<Long, List<AbilityType>> inventories = requireInventories(gameId);
        List<AbilityType> hand = inventories.computeIfAbsent(userId, k -> new ArrayList<>());

        if (hand.size() >= MAX_CARDS_HELD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Inventory is full (" + MAX_CARDS_HELD + " cards max)");
        }
        hand.add(card);
        Set<Long> pending = pendingCardDraw.get(gameId);
        if (pending != null)
            pending.remove(userId);
    }

    public List<AbilityType> getInventory(Long gameId, Long userId) {
        Map<Long, List<AbilityType>> inventories = playerInventories.get(gameId);
        if (inventories == null)
            return Collections.emptyList();
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

    // ── Freeze ────────────────────────────────────────────────────────────────

    public void freezePlayer(Long gameId, Long userId) {
        frozenPlayers.get(gameId).add(userId);
    }

    public boolean isFrozen(Long gameId, Long userId) {
        return frozenPlayers.getOrDefault(gameId, Collections.emptySet()).contains(userId);
    }

    public void clearFreeze(Long gameId, Long userId) {
        Set<Long> frozen = frozenPlayers.get(gameId);
        if (frozen != null)
            frozen.remove(userId);
    }

    // ── Bonus actions ─────────────────────────────────────────────────────────

    public void setBonusAction(Long gameId, Long userId, int count) {
        bonusActions
            .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
            .put(userId, count);  // SET, not add — always replaces existing count
    }

    public void addBonusAction(Long gameId, Long userId) {
        bonusActions
            .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
            .merge(userId, 1, Integer::sum);
    }

    public boolean hasBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap == null)
            return false;
        Integer remaining = gameMap.get(userId);
        return remaining != null && remaining > 0;
    }

    public void consumeBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap == null)
            return;
        gameMap.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1);
    }

    public void clearBonusAction(Long gameId, Long userId) {
        Map<Long, Integer> gameMap = bonusActions.get(gameId);
        if (gameMap != null)
            gameMap.remove(userId);
    }

    // ── Poison zones ──────────────────────────────────────────────────────────

    public void addPoisonZone(Long gameId, int topLeftRow, int topLeftCol) {
        List<PoisonZone> zones = requirePoisonZones(gameId);

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
        if (zones == null || zones.isEmpty())
            return;
        int tick = poisonTurnTick.merge(gameId, 1, Integer::sum);
        int numPlayers = playerCount.getOrDefault(gameId, 1);
        if (tick % numPlayers == 0) {
            zones.forEach(z -> z.setRoundsRemaining(z.getRoundsRemaining() - 1));
            zones.removeIf(z -> z.getRoundsRemaining() <= 0);
        }
    }

    public boolean isPoisoned(Long gameId, int row, int col) {
        List<PoisonZone> zones = poisonZones.get(gameId);
        if (zones == null)
            return false;
        for (PoisonZone z : zones) {
            boolean rowInZone = (row == z.getTopLeftRow() || row == z.getTopLeftRow() + 2);
            boolean colInZone = (col == z.getTopLeftCol() || col == z.getTopLeftCol() + 2);
            if (rowInZone && colInZone)
                return true;
        }
        return false;
    }

    // ── Extra walls ───────────────────────────────────────────────────────────

    public void addExtraWalls(Long gameId, Long userId, int count) {
        Map<Long, Integer> bonuses = extraWalls.get(gameId);
        if (bonuses == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a chaos game");
        }
        int current = bonuses.getOrDefault(userId, 0);
        bonuses.put(userId, current + count);
    }

    public int getExtraWalls(Long gameId, Long userId) {
        Map<Long, Integer> bonuses = extraWalls.get(gameId);
        if (bonuses == null)
            return 0;
        return bonuses.getOrDefault(userId, 0);
    }

    public void incrementPermanentlyConsumedWalls(Long gameId, Long userId) {
        permanentlyConsumedWalls
                .computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .merge(userId, 1, Integer::sum);
    }

    public int getPermanentlyConsumedWalls(Long gameId, Long userId) {
        Map<Long, Integer> map = permanentlyConsumedWalls.get(gameId);
        if (map == null)
            return 0;
        return map.getOrDefault(userId, 0);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    // ── XP system: per-player action tracking ─────────────────────────────────

    public void incrementPlayerMoveCount(Long gameId, Long userId) {
        Map<Long, Integer> counts = playerMoveCount.get(gameId);
        if (counts != null) {
            counts.merge(userId, 1, Integer::sum);
        }
    }

    public void incrementPlayerWallCount(Long gameId, Long userId) {
        Map<Long, Integer> counts = playerWallCount.get(gameId);
        if (counts != null) {
            counts.merge(userId, 1, Integer::sum);
        }
    }

    public int getPlayerMoveCount(Long gameId, Long userId) {
        Map<Long, Integer> counts = playerMoveCount.get(gameId);
        return counts != null ? counts.getOrDefault(userId, 0) : 0;
    }

    public int getPlayerWallCount(Long gameId, Long userId) {
        Map<Long, Integer> counts = playerWallCount.get(gameId);
        return counts != null ? counts.getOrDefault(userId, 0) : 0;
    }

    /** Records a player as eliminated (for 4-player placement ordering). */
    public void recordElimination(Long gameId, Long userId) {
        List<Long> order = eliminationOrder.get(gameId);
        if (order != null && !order.contains(userId)) {
            order.add(userId);
        }
    }

    /**
     * Returns the elimination order (first entry = first eliminated = worst
     * placement).
     */
    public List<Long> getEliminationOrder(Long gameId) {
        List<Long> order = eliminationOrder.get(gameId);
        return order != null ? Collections.unmodifiableList(order) : Collections.emptyList();
    }
}