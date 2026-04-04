package ch.uzh.ifi.hase.soprafs26.entity;

/**
 * Represents a player's pawn on the board.
 * Plain POJO, not persisted to DB, held in GameStateCache.
 *
 * row and col use the 17×17 internal coordinate system (even values only).
 */
public class Pawn {

    private Long id;
    private Long userId;
    private int row;
    private int col;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }

    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }
}
