package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for all active game state (walls and pawns).
 * JPA is not used for game state, this cache is the single source of truth
 * during a game. Cleared when the game ends.
 *
 * Per game, holds:
 *   boolean[17][17] wallGrid  — wall lookups for BFS and conflict checks
 *   List<Wall>       walls    — ordered list sent to the frontend for rendering
 *   List<Pawn>       pawns    — current pawn positions (one per player)
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
 *   initGame  — GameService.createGameFromLobby
 *   placeWall — MoveService.applyWallPlacement
 *   movePawn  — MoveService.processMove
 *   evictGame — GameService.forfeitGame / win handling
 */
@Component
public class GameStateCache {

    private static final int INTERNAL_SIZE = 17;

    private final Map<Long, boolean[][]> wallGrids = new ConcurrentHashMap<>();
    private final Map<Long, List<Wall>>  walls     = new ConcurrentHashMap<>();
    private final Map<Long, List<Pawn>>  pawns     = new ConcurrentHashMap<>();

    /**
     * Initialises an empty wall grid and places pawns at their canonical
     * starting positions for the given player list.
     */
    public void initGame(Long gameId, List<Long> playerIds) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Marks the three cells occupied by the wall in the grid and appends
     * the wall to the render list. Must be called after turn validation.
     */
    public void placeWall(Long gameId, int row, int col, WallOrientation orientation,
                          Long userId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Updates the pawn position for the given player.
     * Must be called after move validation.
     */
    public void movePawn(Long gameId, Long userId, int row, int col) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Returns the wall occupancy grid for.
     * grid[r][c] == true means that cell is blocked by a wall.
     */
    public boolean[][] getWallGrid(Long gameId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /** Returns the ordered wall list for frontend rendering. */
    public List<Wall> getWalls(Long gameId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /** Returns all pawns for the given game. */
    public List<Pawn> getPawns(Long gameId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /** Returns the pawn belonging to the given player, or null if not found. */
    public Pawn getPawn(Long gameId, Long userId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /** Removes all state for a finished game to free memory. */
    public void evictGame(Long gameId) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }
}
