package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.PoisonZone;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateCacheTest {

    private GameStateCache cache;

    @BeforeEach
    public void setup() {
        cache = new GameStateCache();
    }

    @Test
    public void initGame_twoPlayers_setsStartPositions() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        List<Pawn> pawns = cache.getPawns(1L);
        assertEquals(2, pawns.size());

        Pawn p0 = pawns.get(0);
        assertEquals(10L, p0.getUserId());
        assertEquals(16, p0.getRow());
        assertEquals(8, p0.getCol());

        Pawn p1 = pawns.get(1);
        assertEquals(20L, p1.getUserId());
        assertEquals(0, p1.getRow());
        assertEquals(8, p1.getCol());
    }

    @Test
    public void initGame_fourPlayers_setsAllStartPositions() {
        cache.initGame(1L, Arrays.asList(1L, 2L, 3L, 4L), false);

        List<Pawn> pawns = cache.getPawns(1L);
        assertEquals(4, pawns.size());
        assertEquals(8, pawns.get(2).getRow());
        assertEquals(16, pawns.get(2).getCol());
        assertEquals(8, pawns.get(3).getRow());
        assertEquals(0, pawns.get(3).getCol());
    }

    @Test
    public void initGame_tooManyPlayers_throws400() {
        List<Long> tooMany = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.initGame(1L, tooMany, false));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void placeWall_horizontal_marksThreeCellsAndAppendsToList() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.placeWall(1L, 3, 4, WallOrientation.HORIZONTAL, 10L);

        boolean[][] grid = cache.getWallGrid(1L);
        assertTrue(grid[3][3]);
        assertTrue(grid[3][4]);
        assertTrue(grid[3][5]);

        List<Wall> walls = cache.getWalls(1L);
        assertEquals(1, walls.size());
        Wall w = walls.get(0);
        assertEquals(1L, w.getId());
        assertEquals(10L, w.getUserId());
        assertEquals(3, w.getRow());
        assertEquals(4, w.getCol());
        assertEquals(WallOrientation.HORIZONTAL, w.getOrientation());
    }

    @Test
    public void placeWall_vertical_marksThreeCellsAndAppendsToList() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.placeWall(1L, 5, 3, WallOrientation.VERTICAL, 20L);

        boolean[][] grid = cache.getWallGrid(1L);
        assertTrue(grid[4][3]);
        assertTrue(grid[5][3]);
        assertTrue(grid[6][3]);

        List<Wall> walls = cache.getWalls(1L);
        assertEquals(1, walls.size());
        assertEquals(WallOrientation.VERTICAL, walls.get(0).getOrientation());
    }

    @Test
    public void placeWall_multipleWalls_idIncrements() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        cache.placeWall(1L, 1, 1, WallOrientation.HORIZONTAL, 10L);
        cache.placeWall(1L, 3, 1, WallOrientation.HORIZONTAL, 20L);

        List<Wall> walls = cache.getWalls(1L);
        assertEquals(1L, walls.get(0).getId());
        assertEquals(2L, walls.get(1).getId());
    }

    @Test
    public void placeWall_gameMissing_throws404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.placeWall(999L, 1, 1, WallOrientation.HORIZONTAL, 10L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void movePawn_updatesPosition() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.movePawn(1L, 10L, 14, 8);

        Pawn p = cache.getPawn(1L, 10L);
        assertEquals(14, p.getRow());
        assertEquals(8, p.getCol());
    }

    @Test
    public void movePawn_userWithoutPawn_throws404() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.movePawn(1L, 999L, 0, 0));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getWallGrid_unknownGame_throws404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.getWallGrid(42L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getWalls_unknownGame_returnsEmptyList() {
        assertTrue(cache.getWalls(42L).isEmpty());
    }

    @Test
    public void getPawns_unknownGame_returnsEmptyList() {
        assertTrue(cache.getPawns(42L).isEmpty());
    }

    @Test
    public void getPawn_unknownGame_returnsNull() {
        assertNull(cache.getPawn(42L, 10L));
    }

    @Test
    public void getPawn_unknownUser_returnsNull() {
        cache.initGame(1L, Collections.singletonList(10L), false);
        assertNull(cache.getPawn(1L, 999L));
    }

    @Test
    public void evictGame_removesAllState() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        cache.placeWall(1L, 1, 1, WallOrientation.HORIZONTAL, 10L);

        cache.evictGame(1L);

        assertTrue(cache.getPawns(1L).isEmpty());
        assertTrue(cache.getWalls(1L).isEmpty());
        assertThrows(ResponseStatusException.class, () -> cache.getWallGrid(1L));
    }

    // ════════════════════════════════════════════════
    // Wall removal
    // ════════════════════════════════════════════════
    @Test
    public void removeWall_horizontal_clearsThreeCellsAndDropsFromList() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        cache.placeWall(1L, 3, 4, WallOrientation.HORIZONTAL, 10L);

        cache.removeWall(1L, 3, 4, WallOrientation.HORIZONTAL);

        boolean[][] grid = cache.getWallGrid(1L);
        assertFalse(grid[3][3]);
        assertFalse(grid[3][4]);
        assertFalse(grid[3][5]);
        assertTrue(cache.getWalls(1L).isEmpty());
    }

    @Test
    public void removeWall_vertical_clearsThreeCellsAndDropsFromList() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        cache.placeWall(1L, 5, 3, WallOrientation.VERTICAL, 20L);

        cache.removeWall(1L, 5, 3, WallOrientation.VERTICAL);

        boolean[][] grid = cache.getWallGrid(1L);
        assertFalse(grid[4][3]);
        assertFalse(grid[5][3]);
        assertFalse(grid[6][3]);
    }

    @Test
    public void removeWall_unknownGame_throws404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.removeWall(99L, 3, 4, WallOrientation.HORIZONTAL));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ════════════════════════════════════════════════
    // Chaos-mode initialization
    // ════════════════════════════════════════════════
    @Test
    public void initGame_chaosMode_initializesInventoriesAndPoisonAndTurnCounter() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        assertNotNull(cache.getInventory(1L, 10L));
        assertTrue(cache.getInventory(1L, 10L).isEmpty());
        assertTrue(cache.getPoisonZones(1L).isEmpty());
        assertEquals(0, cache.getTurnCounter(1L));
        assertEquals(0, cache.getExtraWalls(1L, 10L));
    }

    @Test
    public void initGame_classicMode_doesNotInitChaosStructures() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        assertTrue(cache.getInventory(1L, 10L).isEmpty());
        // Extra walls map is null in classic mode
        assertEquals(0, cache.getExtraWalls(1L, 10L));
    }

    // ════════════════════════════════════════════════
    // Inventory (chaos)
    // ════════════════════════════════════════════════
    @Test
    public void grantCard_appendsToHand() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        cache.grantCard(1L, 10L, AbilityType.FIREBALL);

        List<AbilityType> hand = cache.getInventory(1L, 10L);
        assertEquals(1, hand.size());
        assertEquals(AbilityType.FIREBALL, hand.get(0));
    }

    @Test
    public void grantCard_atMaxCapacity_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.grantCard(1L, 10L, AbilityType.FIREBALL);
        cache.grantCard(1L, 10L, AbilityType.POISON);
        cache.grantCard(1L, 10L, AbilityType.FREEZE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.grantCard(1L, 10L, AbilityType.EARTHQUAKE));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void grantCard_classicMode_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        assertThrows(ResponseStatusException.class,
                () -> cache.grantCard(1L, 10L, AbilityType.FIREBALL));
    }

    @Test
    public void removeCardFromInventory_existingCard_drops() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.grantCard(1L, 10L, AbilityType.FIREBALL);

        cache.removeCardFromInventory(1L, 10L, AbilityType.FIREBALL);

        assertTrue(cache.getInventory(1L, 10L).isEmpty());
    }

    @Test
    public void removeCardFromInventory_missingCard_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.removeCardFromInventory(1L, 10L, AbilityType.FIREBALL));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void getInventory_unknownGame_returnsEmpty() {
        assertTrue(cache.getInventory(99L, 10L).isEmpty());
    }

    @Test
    public void getAllInventories_returnsAllPlayerHands() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.grantCard(1L, 10L, AbilityType.FIREBALL);

        Map<Long, List<AbilityType>> all = cache.getAllInventories(1L);
        assertEquals(2, all.size());
        assertEquals(1, all.get(10L).size());
        assertTrue(all.get(20L).isEmpty());
    }

    @Test
    public void getAllInventories_classicMode_returnsEmpty() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);
        assertTrue(cache.getAllInventories(1L).isEmpty());
    }

    // ════════════════════════════════════════════════
    // Turn counter & card draw eligibility
    // ════════════════════════════════════════════════
    @Test
    public void incrementTurnCounter_below6_doesNotGrantDraw() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        for (int i = 0; i < 5; i++) {
            cache.incrementTurnCounter(1L, List.of(10L, 20L));
        }

        assertFalse(cache.hasPendingCardDraw(1L, 10L));
        assertEquals(5, cache.getTurnCounter(1L));
    }

    @Test
    public void incrementTurnCounter_atSixth_grantsDrawToAll() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        for (int i = 0; i < 6; i++) {
            cache.incrementTurnCounter(1L, List.of(10L, 20L));
        }

        assertTrue(cache.hasPendingCardDraw(1L, 10L));
        assertTrue(cache.hasPendingCardDraw(1L, 20L));
    }

    @Test
    public void incrementTurnCounter_classicMode_doesNothing() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.incrementTurnCounter(1L, List.of(10L, 20L));
        // Classic mode does not init turnCounter map → counter stays at 0
        assertEquals(0, cache.getTurnCounter(1L));
    }

    @Test
    public void incrementTurnCounter_handFullSkipsPlayer() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.grantCard(1L, 10L, AbilityType.FIREBALL);
        cache.grantCard(1L, 10L, AbilityType.POISON);
        cache.grantCard(1L, 10L, AbilityType.FREEZE);

        for (int i = 0; i < 6; i++) {
            cache.incrementTurnCounter(1L, List.of(10L, 20L));
        }

        // Player 10 has full hand → no pending draw
        assertFalse(cache.hasPendingCardDraw(1L, 10L));
        assertTrue(cache.hasPendingCardDraw(1L, 20L));
    }

    @Test
    public void hasPendingCardDraw_unknownGame_returnsFalse() {
        assertFalse(cache.hasPendingCardDraw(99L, 10L));
    }

    // ════════════════════════════════════════════════
    // Freeze
    // ════════════════════════════════════════════════
    @Test
    public void freeze_setAndClear() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        assertFalse(cache.isFrozen(1L, 10L));
        cache.freezePlayer(1L, 10L);
        assertTrue(cache.isFrozen(1L, 10L));

        cache.clearFreeze(1L, 10L);
        assertFalse(cache.isFrozen(1L, 10L));
    }

    @Test
    public void isFrozen_unknownGame_returnsFalse() {
        assertFalse(cache.isFrozen(99L, 10L));
    }

    @Test
    public void clearFreeze_unknownGame_doesNotThrow() {
        cache.clearFreeze(99L, 10L);
    }

    // ════════════════════════════════════════════════
    // Bonus actions
    // ════════════════════════════════════════════════
    @Test
    public void bonusAction_setAndConsume() {
        cache.setBonusAction(1L, 10L, 2);
        assertTrue(cache.hasBonusAction(1L, 10L));

        cache.consumeBonusAction(1L, 10L);
        assertTrue(cache.hasBonusAction(1L, 10L));

        cache.consumeBonusAction(1L, 10L);
        assertFalse(cache.hasBonusAction(1L, 10L));
    }

    @Test
    public void hasBonusAction_unknownGame_returnsFalse() {
        assertFalse(cache.hasBonusAction(99L, 10L));
    }

    @Test
    public void hasBonusAction_unknownUser_returnsFalse() {
        cache.setBonusAction(1L, 10L, 1);
        assertFalse(cache.hasBonusAction(1L, 99L));
    }

    @Test
    public void consumeBonusAction_unknownGame_doesNotThrow() {
        cache.consumeBonusAction(99L, 10L);
    }

    @Test
    public void clearBonusAction_removes() {
        cache.setBonusAction(1L, 10L, 3);
        cache.clearBonusAction(1L, 10L);
        assertFalse(cache.hasBonusAction(1L, 10L));
    }

    @Test
    public void clearBonusAction_unknownGame_doesNotThrow() {
        cache.clearBonusAction(99L, 10L);
    }

    // ════════════════════════════════════════════════
    // Poison zones
    // ════════════════════════════════════════════════

    // Kept from main2: uses (4,4) and expects roundsRemaining = 2
    @Test
    public void addPoisonZone_addsToList() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.addPoisonZone(1L, 4, 4);

        List<PoisonZone> zones = cache.getPoisonZones(1L);
        assertEquals(1, zones.size());
        assertEquals(4, zones.get(0).getTopLeftRow());
        assertEquals(4, zones.get(0).getTopLeftCol());
        assertEquals(2, zones.get(0).getRoundsRemaining());
    }

    @Test
    public void addPoisonZone_overlapsPawn_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        // Pawn 1 starts at (16, 8). Try to poison covering (16, 8).
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.addPoisonZone(1L, 16, 8));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void addPoisonZone_classicMode_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        assertThrows(ResponseStatusException.class, () -> cache.addPoisonZone(1L, 6, 6));
    }

    // Kept from main2: 2 players → full round = 2 ticks; roundsRemaining drops only after each full round
    @Test
    public void tickPoisonZones_decrementsRoundsRemaining() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.addPoisonZone(1L, 4, 4);

        cache.tickPoisonZones(1L); // player 1's turn — not yet a full round
        assertEquals(2, cache.getPoisonZones(1L).get(0).getRoundsRemaining());

        cache.tickPoisonZones(1L); // player 2's turn — full round complete
        assertEquals(1, cache.getPoisonZones(1L).get(0).getRoundsRemaining());
    }

    @Test
    public void tickPoisonZones_removesExpired() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.addPoisonZone(1L, 6, 6);

        for (int i = 0; i < 4; i++) {
            cache.tickPoisonZones(1L);
        }

        assertTrue(cache.getPoisonZones(1L).isEmpty());
    }

    @Test
    public void tickPoisonZones_unknownGame_doesNotThrow() {
        cache.tickPoisonZones(99L);
    }

    @Test
    public void isPoisoned_inZone_returnsTrue() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.addPoisonZone(1L, 6, 6);

        // Zone covers (6,6), (6,8), (8,6), (8,8)
        assertTrue(cache.isPoisoned(1L, 6, 6));
        assertTrue(cache.isPoisoned(1L, 6, 8));
        assertTrue(cache.isPoisoned(1L, 8, 6));
        assertTrue(cache.isPoisoned(1L, 8, 8));
    }

    @Test
    public void isPoisoned_outsideZone_returnsFalse() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);
        cache.addPoisonZone(1L, 6, 6);

        assertFalse(cache.isPoisoned(1L, 7, 7));
        assertFalse(cache.isPoisoned(1L, 0, 0));
    }

    @Test
    public void isPoisoned_unknownGame_returnsFalse() {
        assertFalse(cache.isPoisoned(99L, 0, 0));
    }

    // ════════════════════════════════════════════════
    // Extra walls
    // ════════════════════════════════════════════════
    @Test
    public void addExtraWalls_increments() {
        cache.initGame(1L, Arrays.asList(10L, 20L), true);

        cache.addExtraWalls(1L, 10L, 2);
        assertEquals(2, cache.getExtraWalls(1L, 10L));

        cache.addExtraWalls(1L, 10L, 1);
        assertEquals(3, cache.getExtraWalls(1L, 10L));
    }

    @Test
    public void addExtraWalls_classicMode_throwsBadRequest() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        assertThrows(ResponseStatusException.class, () -> cache.addExtraWalls(1L, 10L, 1));
    }

    @Test
    public void permanentlyConsumedWalls_incrementAndQuery() {
        cache.incrementPermanentlyConsumedWalls(1L, 10L);
        cache.incrementPermanentlyConsumedWalls(1L, 10L);

        assertEquals(2, cache.getPermanentlyConsumedWalls(1L, 10L));
        assertEquals(0, cache.getPermanentlyConsumedWalls(1L, 99L));
        assertEquals(0, cache.getPermanentlyConsumedWalls(99L, 10L));
    }

    // ════════════════════════════════════════════════
    // XP system: action counters
    // ════════════════════════════════════════════════
    @Test
    public void incrementPlayerMoveCount_classic_increases() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.incrementPlayerMoveCount(1L, 10L);
        cache.incrementPlayerMoveCount(1L, 10L);

        assertEquals(2, cache.getPlayerMoveCount(1L, 10L));
        assertEquals(0, cache.getPlayerMoveCount(1L, 20L));
    }

    @Test
    public void incrementPlayerWallCount_classic_increases() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.incrementPlayerWallCount(1L, 10L);

        assertEquals(1, cache.getPlayerWallCount(1L, 10L));
    }

    @Test
    public void counts_unknownGame_returnZero() {
        assertEquals(0, cache.getPlayerMoveCount(99L, 10L));
        assertEquals(0, cache.getPlayerWallCount(99L, 10L));
        // increment is no-op for unknown game
        cache.incrementPlayerMoveCount(99L, 10L);
        cache.incrementPlayerWallCount(99L, 10L);
    }

    // ════════════════════════════════════════════════
    // Elimination tracking
    // ════════════════════════════════════════════════
    @Test
    public void recordElimination_appendsInOrder() {
        cache.initGame(1L, Arrays.asList(10L, 20L, 30L), false);

        cache.recordElimination(1L, 20L);
        cache.recordElimination(1L, 30L);

        List<Long> order = cache.getEliminationOrder(1L);
        assertEquals(List.of(20L, 30L), order);
    }

    @Test
    public void recordElimination_duplicate_isIgnored() {
        cache.initGame(1L, Arrays.asList(10L, 20L), false);

        cache.recordElimination(1L, 10L);
        cache.recordElimination(1L, 10L);

        assertEquals(1, cache.getEliminationOrder(1L).size());
    }

    @Test
    public void getEliminationOrder_unknownGame_returnsEmpty() {
        assertTrue(cache.getEliminationOrder(99L).isEmpty());
    }
}