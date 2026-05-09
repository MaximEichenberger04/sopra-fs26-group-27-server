package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Handles XP, leveling, and coin rewards for the Quoridor Chaos Arena.
 *
 * <h3>XP from in-game actions (earned during gameplay)</h3>
 * <ul>
 * <li>Every pawn move earns 2 XP</li>
 * <li>Every wall placed earns 5 XP</li>
 * <li>If a player forfeits, they lose all action XP from that game</li>
 * </ul>
 *
 * <h3>XP from game result (awarded when game ends)</h3>
 * <ul>
 * <li>2-player: Winner +100 XP, Loser +30 XP, Forfeit = 0 XP</li>
 * <li>4-player: 1st +150, 2nd +80, 3rd +40, 4th/Forfeit = 0 XP</li>
 * </ul>
 *
 * <h3>Leveling curve</h3>
 * XP required to reach the next level = currentLevel × 130.
 * Total XP to reach level L = sum(i=1..L-1) of i×130 = 130 × L×(L-1)/2
 *
 * <h3>Coin rewards per level-up</h3>
 * Level 2-5: 100 coins, 6-10: 150, 11-15: 200, 16-20: 300,
 * 21-30: 450, 31-40: 650, 41-50: 900, 51+: 1200
 */
@Service
public class LevelingService {

    private static final int XP_PER_MOVE = 2;
    private static final int XP_PER_WALL = 5;

    // 2-player result XP
    private static final int XP_WIN_2P = 100;
    private static final int XP_LOSE_2P = 30;

    // 4-player result XP by placement (index 0 = 1st place)
    private static final int[] XP_4P_BY_PLACEMENT = { 150, 80, 40, 0 };

    private static final int XP_PER_LEVEL_MULTIPLIER = 130;

    private final UserRepository userRepository;

    public LevelingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Calculates action XP earned from moves and walls.
     * Returns 0 if the player forfeited.
     */
    public int calculateActionXp(int moveCount, int wallCount, boolean forfeited) {
        if (forfeited) {
            return 0;
        }
        return (moveCount * XP_PER_MOVE) + (wallCount * XP_PER_WALL);
    }

    /**
     * Calculates result XP for a 2-player game.
     */
    public int calculateResultXp2Player(boolean won, boolean forfeited) {
        if (forfeited) {
            return 0;
        }
        return won ? XP_WIN_2P : XP_LOSE_2P;
    }

    /**
     * Calculates result XP for a 4-player game based on placement (1-4).
     * Placement 4 or forfeit = 0 XP.
     */
    public int calculateResultXp4Player(int placement, boolean forfeited) {
        if (forfeited || placement < 1 || placement > 4) {
            return 0;
        }
        return XP_4P_BY_PLACEMENT[placement - 1];
    }

    /**
     * Returns the total cumulative XP needed to reach the given level.
     * Level 1 requires 0 XP (starting level).
     * Total XP for level L = 130 × L × (L-1) / 2
     */
    public int totalXpForLevel(int level) {
        if (level <= 1)
            return 0;
        return XP_PER_LEVEL_MULTIPLIER * level * (level - 1) / 2;
    }

    /**
     * Computes the level for a given total XP amount.
     * Solves: 130 × L × (L-1) / 2 <= totalXp
     */
    public int computeLevel(int totalXp) {
        if (totalXp <= 0)
            return 1;
        // Solve quadratic: 65 * L^2 - 65 * L - totalXp <= 0
        // L = (65 + sqrt(65^2 + 4*65*totalXp)) / (2*65)
        double discriminant = 65.0 * 65.0 + 4.0 * 65.0 * totalXp;
        int level = (int) ((65.0 + Math.sqrt(discriminant)) / 130.0);
        // Verify and adjust (floating point edge cases)
        while (totalXpForLevel(level + 1) <= totalXp) {
            level++;
        }
        return Math.max(1, level);
    }

    /**
     * Returns the coin reward for reaching a specific level.
     */
    public int coinRewardForLevel(int level) {
        if (level <= 1)
            return 0;
        if (level <= 5)
            return 100;
        if (level <= 10)
            return 150;
        if (level <= 15)
            return 200;
        if (level <= 20)
            return 300;
        if (level <= 30)
            return 450;
        if (level <= 40)
            return 650;
        if (level <= 50)
            return 900;
        return 1200;
    }

    /**
     * Awards XP to a player, handles leveling up (possibly multiple levels),
     * and awards coins for each level gained. Persists the user.
     *
     * @param user      the user entity
     * @param xpToAward total XP to award (action + result)
     * @return the number of levels gained
     */
    public int awardXp(User user, int xpToAward) {
        if (xpToAward <= 0)
            return 0;

        int oldLevel = user.getLevel();
        if (oldLevel < 1)
            oldLevel = 1; // safety for legacy users

        int newTotalXp = user.getXp() + xpToAward;
        int newLevel = computeLevel(newTotalXp);

        user.setXp(newTotalXp);
        user.setLevel(newLevel);

        // Award coins for each level gained
        int coinsEarned = 0;
        for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
            coinsEarned += coinRewardForLevel(lvl);
        }
        if (coinsEarned > 0) {
            user.setCoins(user.getCoins() + coinsEarned);
        }

        userRepository.save(user);
        return newLevel - oldLevel;
    }
}