package ch.uzh.ifi.hase.soprafs26.entity;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;

/**
 * Represents a wall placed on the board.
 * Plain POJO — not persisted to DB; held in GameStateCache.
 *
 * row and col store the center intersection in the 17×17 internal grid (both odd).
 * A HORIZONTAL wall at (row, col) occupies cells: (row, col-1), (row, col), (row, col+1)
 * A VERTICAL   wall at (row, col) occupies cells: (row-1, col), (row, col), (row+1, col)
 */
public class Wall {

    private Long id;
    private Long userId;
    private int row;
    private int col;
    private WallOrientation orientation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }

    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }

    public WallOrientation getOrientation() { return orientation; }
    public void setOrientation(WallOrientation orientation) { this.orientation = orientation; }
}
