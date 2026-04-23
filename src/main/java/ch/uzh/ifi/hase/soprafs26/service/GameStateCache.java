package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final int[][] START_POSITIONS = {
        {16, 8}, // player 0, starts bottom center, moves north
        {0, 8},   // player 1, starts top center, moves south
        {8, 16}, // player 2 starts on the right, moves left
        {8, 0}  //player 3 starts on left, moves right
    };

    private final Map<Long, boolean[][]> wallGrids = new ConcurrentHashMap<>();
    private final Map<Long, List<Wall>>  walls     = new ConcurrentHashMap<>();
    private final Map<Long, List<Pawn>>  pawns     = new ConcurrentHashMap<>();

    /**
     * Initialises an empty wall grid and places pawns at their
     * starting positions for the given player list.
     */
    public void initGame(Long gameId, List<Long> playerIds) {
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

    // Removes all state for a finished game to free memory.
    public void evictGame(Long gameId) {
        wallGrids.remove(gameId);
        walls.remove(gameId);
        pawns.remove(gameId);
    }
}
