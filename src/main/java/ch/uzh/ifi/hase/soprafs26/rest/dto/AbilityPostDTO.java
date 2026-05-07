package ch.uzh.ifi.hase.soprafs26.rest.dto;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;

/**
 * Request body for POST /games/{gameId}/ability
 *
 * Field usage by ability type:
 *
 *   FIREBALL        – targetRow + targetCol required (top-left corner of 2×2 field, logical coords)
 *   EARTHQUAKE      – targetRow + targetCol required (center of 3×3 field, logical coords)
 *   POISON          – targetRow + targetCol required (top-left corner of 2×2 zone, logical coords)
 *   FREEZE          – targetUserId required (the opponent to freeze)
 *   PLUS_TWO_WALLS  – no extra fields needed
 *   TWO_MOVES       – no extra fields needed
 *
 * "logical coords" means the 9×9 board space (0–8), NOT the 17×17 internal grid.
 * AbilityService converts to internal coords internally (multiply by 2).
 */
public class AbilityPostDTO {

    private AbilityType abilityType;
    private Integer targetRow;
    private Integer targetCol;
    private Long targetUserId;

    public AbilityType getAbilityType() { return abilityType; }
    public void setAbilityType(AbilityType abilityType) { this.abilityType = abilityType; }

    public Integer getTargetRow() { return targetRow; }
    public void setTargetRow(Integer targetRow) { this.targetRow = targetRow; }

    public Integer getTargetCol() { return targetCol; }
    public void setTargetCol(Integer targetCol) { this.targetCol = targetCol; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
}