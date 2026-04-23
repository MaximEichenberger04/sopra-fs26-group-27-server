package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateCacheTest {

    private GameStateCache cache;

    @BeforeEach
    public void setup() {
        cache = new GameStateCache();
    }

    @Test
    public void initGame_twoPlayers_setsStartPositions() {
        cache.initGame(1L, Arrays.asList(10L, 20L));

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
        cache.initGame(1L, Arrays.asList(1L, 2L, 3L, 4L));

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
                () -> cache.initGame(1L, tooMany));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void placeWall_horizontal_marksThreeCellsAndAppendsToList() {
        cache.initGame(1L, Arrays.asList(10L, 20L));

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
        cache.initGame(1L, Arrays.asList(10L, 20L));

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
        cache.initGame(1L, Arrays.asList(10L, 20L));
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
        cache.initGame(1L, Arrays.asList(10L, 20L));

        cache.movePawn(1L, 10L, 14, 8);

        Pawn p = cache.getPawn(1L, 10L);
        assertEquals(14, p.getRow());
        assertEquals(8, p.getCol());
    }

    @Test
    public void movePawn_userWithoutPawn_throws404() {
        cache.initGame(1L, Arrays.asList(10L, 20L));
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
        cache.initGame(1L, Collections.singletonList(10L));
        assertNull(cache.getPawn(1L, 999L));
    }

    @Test
    public void evictGame_removesAllState() {
        cache.initGame(1L, Arrays.asList(10L, 20L));
        cache.placeWall(1L, 1, 1, WallOrientation.HORIZONTAL, 10L);

        cache.evictGame(1L);

        assertTrue(cache.getPawns(1L).isEmpty());
        assertTrue(cache.getWalls(1L).isEmpty());
        assertThrows(ResponseStatusException.class, () -> cache.getWallGrid(1L));
    }
}
