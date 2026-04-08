package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import java.util.Queue;
import java.util.LinkedList;

/**
 * Handles pawn moves and wall placements for a Quoridor game.
 *
 * All validation (wall lookups, BFS, pawn adjacency) runs against the
 * in-memory GameStateCache, no DB is used.
 *
 * Coordinate system, 17×17 internal grid (for a standard 9×9 board):
 *   Pawn cells:          even row, even col
 *   Wall intersections:  odd  row, odd  col  (center of wall)
 *   H-wall occupies:     (row, col-1), (row, col), (row, col+1)
 *   V-wall occupies:     (row-1, col), (row, col), (row+1, col)
 *
 * Move rules:
 *   SIMPLE   – step one pawn cell in a cardinal direction: row=±2 or col=±2
 *   JUMP     – leap over an adjacent opponent: row=±4 or col=±4 (no wall between)
 *   DIAGONAL – straight jump blocked (wall or edge) → move diagonally: row=±2, col=±2
 */
@Service
@Transactional
public class MoveService {

    private static final int INTERNAL_SIZE = 17; // 2 * 9 - 1

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameService gameService;
    private final GameStateCache gameStateCache;
    private final GameWebSocketHandler gameWebSocketHandler;

    public MoveService(
            @Qualifier("gameRepository") GameRepository gameRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            GameService gameService,
            GameStateCache gameStateCache,
            GameWebSocketHandler gameWebSocketHandler) {
        this.gameRepository       = gameRepository;
        this.userRepository       = userRepository;
        this.gameService          = gameService;
        this.gameStateCache       = gameStateCache;
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    // ─────────────────────────────────────────────────────────────
    //  Pawn move
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates and applies a pawn move.
     * Reads pawn position and wall grid from GameStateCache.
     * Updates the cache, checks win, advances turn, broadcasts refresh (through webseocket).
     *
     * @throws org.springframework.web.server.ResponseStatusException 403 if not the caller's turn
     * @throws org.springframework.web.server.ResponseStatusException 400 if the move is invalid
     */
    public GameGetDTO processMove(Long gameId, MovePostDTO dto, String token) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Wall placement
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates and applies a wall placement.
     * Checks budget, bounds, overlap, and path-blocking (BFS), all against the cache grid.
     * Updates the cache, advances turn, broadcasts refresh (through webseocket).
     *
     * @throws org.springframework.web.server.ResponseStatusException 403 if not the caller's turn
     * @throws org.springframework.web.server.ResponseStatusException 400 on any invalid placement
     */
    public GameGetDTO applyWallPlacement(Long gameId, WallPostDTO dto, String token) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Move validation
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if moving {@code pawn} to (targetRow, targetCol) is a legal Quoridor move.
     * Checks SIMPLE, JUMP, and DIAGONAL cases using the O(1) grid.
     */
    private boolean isValidPawnMove(Pawn pawn, int targetRow, int targetCol,
                                    List<Pawn> allPawns, boolean[][] grid) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Diagonal move is legal when the straight jump is blocked (wall or board edge)
     * but an opponent is adjacent in one of the two cardinal components.
     */
    private boolean isDiagonalValid(Pawn pawn, int dr, int dc,
                                    List<Pawn> allPawns, boolean[][] grid) {
        // TODO
        throw new UnsupportedOperationException("not implemented");
    }

    // ─────────────────────────────────────────────────────────────
    //  Wall helpers  (all O(1) via the boolean[][] grid)
    // ─────────────────────────────────────────────────────────────

    // Returns true if grid[r][c] is occupied by any wall segment.
    private boolean isWallAt(boolean[][] grid, int r, int c) {
        if (r < 0 || r >= INTERNAL_SIZE || c < 0 || c >= INTERNAL_SIZE) {
            return false;
        }
        return grid[r][c];
    }

    /**
     * Returns true if placing a wall at (row, col, orientation) would overlap
     * any already-occupied cell in the grid.
     */
    private boolean wallOverlaps(boolean[][] grid, int row, int col, WallOrientation orientation) {
        if (orientation == WallOrientation.HORIZONTAL) {
            return isWallAt(grid, row, col - 1) || isWallAt(grid, row, col) || isWallAt(grid, row, col + 1);
        } else {
            return isWallAt(grid, row - 1, col) || isWallAt(grid, row, col) || isWallAt(grid, row + 1, col);
        }
    }

    /** Returns the number of walls already placed by the given player. */
    private long countWallsUsedByPlayer(List<Wall> walls, Long userId) {
        int wallCount = 0;
        for (Wall w : walls) {
            if (w.getUserId().equals(userId)) {
                wallCount++;
            }
        }
        return wallCount;
    }

    // ─────────────────────────────────────────────────────────────
    //  BFS path finding  
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if there is a path from (startRow, startCol) to targetRow (any column).
     * Movement restricted to pawn cells (even, even), step size 2.
     */
    public boolean hasPathToGoalRow(boolean[][] grid, int startRow, int startCol, int targetRow) {
        return bfs(grid, startRow, startCol, targetRow, -1);
    }

    // Returns true if there is a path from (startRow, startCol) to targetCol (any row).
    public boolean hasPathToGoalCol(boolean[][] grid, int startRow, int startCol, int targetCol) {
        return bfs(grid, startRow, startCol, -1, targetCol);
    }

    /**
     * BFS over pawn cells (step size 2). Checks grid[midR][midC] to detect walls.
     *
     * @param targetRow pass -1 to ignore row goal
     * @param targetCol pass -1 to ignore col goal
     */
    private boolean bfs(boolean[][] grid, int startRow, int startCol, int targetRow, int targetCol) {
        boolean[][] visited = new boolean[INTERNAL_SIZE][INTERNAL_SIZE];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;

        int dir[][] = {
        {-2, 0},    // up
        {2, 0},     // down
        {0, -2},    // left
        {0, 2}      // right
        };
        while (!queue.isEmpty()) {
            int current[] = queue.poll();
            int row = current[0];
            int col = current[1];

            if (targetRow != -1 && row == targetRow) {
            return true;
            }
            if (targetCol != -1 && col == targetCol) {
                return true;
            }

            for (int d[] : dir){
                int newRow = row + d[0];
                int newCol = col + d[1];

                if (!isValidPawnCell(newRow, newCol)){continue;}
                if (visited[newRow][newCol]) {continue;}

                int midRow = (row + newRow) / 2;
                int midCol = (col + newCol) / 2;
                if (isWallAt(grid, midRow, midCol)) {continue;}

                visited[newRow][newCol] = true;
                queue.add(new int[]{newRow, newCol});
            }
        }
        return false;
    }

    private boolean isValidPawnCell(int row, int col){
        return row >= 0 && row < INTERNAL_SIZE && col >= 0 && col < INTERNAL_SIZE
                && row % 2 == 0 && col % 2 == 0;
    }

    // ─────────────────────────────────────────────────────────────
    //  Pawn presence helper
    // ─────────────────────────────────────────────────────────────

    // Returns true if any pawn other than {@code exclude} is at (r, c). 
    private boolean isPawnAt(List<Pawn> allPawns, Pawn exclude, int r, int c) {
        for (Pawn p : allPawns) {
            if (p != exclude && p.getRow() == r && p.getCol() == c) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    //  Shared guards (for processMove, applyWallPlacement)
    // ─────────────────────────────────────────────────────────────

    //  look up user by token, throw 401 if not found    
    private User requireUser(String token) {
        User user = userRepository.findByToken(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        return user;
    }

    // load game from DB, throw 404 if not found
    private Game requireGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
    }

  
    // Throws 400 if the game is not RUNNING, 403 if it is not this user's turn. 
    private void requireTurn(Game game, Long userId) {
        if (game.getStatus() != RUNNING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game is not running");
        }
        if (!game.getCurrentTurnUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your turn");
        }
    }
}
