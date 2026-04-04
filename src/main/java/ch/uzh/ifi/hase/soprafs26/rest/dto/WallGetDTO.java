package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;

public class WallGetDTO {

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
