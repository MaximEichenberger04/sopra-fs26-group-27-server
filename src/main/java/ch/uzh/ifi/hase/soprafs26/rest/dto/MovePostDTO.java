package ch.uzh.ifi.hase.soprafs26.rest.dto;

/**
 * Request body for POST /games/{id}/move
 * targetField[0] = row, targetField[1] = col (17×17 internal grid, even values)
 */
public class MovePostDTO {

    private int[] targetField;

    public int[] getTargetField() { return targetField; }
    public void setTargetField(int[] targetField) { this.targetField = targetField; }
}
