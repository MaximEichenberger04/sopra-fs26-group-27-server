package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;

/**
 * Request body for POST /games/{id}/wall
 * targetField[0] = row, targetField[1] = col — center intersection (odd, odd) in 17×17 grid
 * orientation = HORIZONTAL or VERTICAL
 */
public class WallPostDTO {

    private int[] targetField;
    private WallOrientation orientation;

    public int[] getTargetField() { return targetField; }
    public void setTargetField(int[] targetField) { this.targetField = targetField; }

    public WallOrientation getOrientation() { return orientation; }
    public void setOrientation(WallOrientation orientation) { this.orientation = orientation; }
}
