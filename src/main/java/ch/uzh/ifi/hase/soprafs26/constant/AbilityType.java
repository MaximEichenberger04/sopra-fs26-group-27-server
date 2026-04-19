package ch.uzh.ifi.hase.soprafs26.constant;
 
/**
 * All six ability card types available in Chaos mode.
 *
 * Positional abilities (require targetRow + targetCol in the request):
 *   FIREBALL   – destroys every wall segment inside a 2×2 logical field
 *   EARTHQUAKE – targeted 3×3 field: shifts walls randomly, 50 % chance of destruction per wall
 *   POISON     – marks a 2×2 field as impassable for 3 rounds; decays after enemy move + friendly move
 *
 * Player-targeted abilities (require targetUserId in the request):
 *   FREEZE     – opponent skips their next turn; caster gets an additional action this turn
 *
 * Self-targeted / no-target abilities:
 *   PLUS_TWO_WALLS – adds 2 walls to caster's budget (hard cap: 10); caster gets an additional action
 *   TWO_MOVES      – caster may perform 2 actions this turn (move/wall/ability in any combination)
 */
public enum AbilityType {
    FIREBALL,
    EARTHQUAKE,
    FREEZE,
    POISON,
    PLUS_TWO_WALLS,
    TWO_MOVES
}