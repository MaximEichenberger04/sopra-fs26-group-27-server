package ch.uzh.ifi.hase.soprafs26.rest.dto;

/**
 * Read-only representation of an active poison zone sent to the frontend.
 * The frontend uses topLeftRow/topLeftCol (17×17 internal grid coords) to
 * render the 2×2 overlay on the board — same coordinate system as walls and pawns.
 */
public class PoisonZoneDTO {

    private Long id;

    /** Top-left corner in 17×17 internal grid coordinates (even, even). */
    private int topLeftRow;
    private int topLeftCol;

    /** How many more turn-advances until the zone disappears (starts at 3). */
    private int roundsRemaining;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public int getTopLeftRow() { return topLeftRow; }
    public void setTopLeftRow(int topLeftRow) { this.topLeftRow = topLeftRow; }

    public int getTopLeftCol() { return topLeftCol; }
    public void setTopLeftCol(int topLeftCol) { this.topLeftCol = topLeftCol; }

    public int getRoundsRemaining() { return roundsRemaining; }
    public void setRoundsRemaining(int roundsRemaining) { this.roundsRemaining = roundsRemaining; }
}