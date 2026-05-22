package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LevelingServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LevelingService levelingService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------- calculateActionXp ----------
    @Test
    public void calculateActionXp_forfeited_returnsZero() {
        assertEquals(0, levelingService.calculateActionXp(50, 10, true));
    }

    @Test
    public void calculateActionXp_movesAndWalls_returnsWeightedSum() {
        // 10 moves * 2 + 4 walls * 5 = 20 + 20 = 40
        assertEquals(40, levelingService.calculateActionXp(10, 4, false));
    }

    @Test
    public void calculateActionXp_zeroActions_returnsZero() {
        assertEquals(0, levelingService.calculateActionXp(0, 0, false));
    }

    // ---------- calculateResultXp2Player ----------
    @Test
    public void calculateResultXp2Player_won_returns100() {
        assertEquals(100, levelingService.calculateResultXp2Player(true, false));
    }

    @Test
    public void calculateResultXp2Player_lost_returns30() {
        assertEquals(30, levelingService.calculateResultXp2Player(false, false));
    }

    @Test
    public void calculateResultXp2Player_forfeited_returnsZero() {
        assertEquals(0, levelingService.calculateResultXp2Player(false, true));
        assertEquals(0, levelingService.calculateResultXp2Player(true, true));
    }

    // ---------- calculateResultXp4Player ----------
    @Test
    public void calculateResultXp4Player_first_returns150() {
        assertEquals(150, levelingService.calculateResultXp4Player(1, false));
    }

    @Test
    public void calculateResultXp4Player_second_returns80() {
        assertEquals(80, levelingService.calculateResultXp4Player(2, false));
    }

    @Test
    public void calculateResultXp4Player_third_returns40() {
        assertEquals(40, levelingService.calculateResultXp4Player(3, false));
    }

    @Test
    public void calculateResultXp4Player_fourth_returnsZero() {
        assertEquals(0, levelingService.calculateResultXp4Player(4, false));
    }

    @Test
    public void calculateResultXp4Player_forfeited_returnsZero() {
        assertEquals(0, levelingService.calculateResultXp4Player(1, true));
        assertEquals(0, levelingService.calculateResultXp4Player(2, true));
    }

    @Test
    public void calculateResultXp4Player_invalidPlacement_returnsZero() {
        assertEquals(0, levelingService.calculateResultXp4Player(0, false));
        assertEquals(0, levelingService.calculateResultXp4Player(5, false));
        assertEquals(0, levelingService.calculateResultXp4Player(-1, false));
    }

    // ---------- totalXpForLevel ----------
    @Test
    public void totalXpForLevel_level1_returnsZero() {
        assertEquals(0, levelingService.totalXpForLevel(1));
    }

    @Test
    public void totalXpForLevel_zeroOrNegative_returnsZero() {
        assertEquals(0, levelingService.totalXpForLevel(0));
        assertEquals(0, levelingService.totalXpForLevel(-3));
    }

    @Test
    public void totalXpForLevel_specificLevels_correct() {
        // 130 * L * (L-1) / 2
        assertEquals(130, levelingService.totalXpForLevel(2));
        assertEquals(390, levelingService.totalXpForLevel(3));
        assertEquals(780, levelingService.totalXpForLevel(4));
        assertEquals(1300, levelingService.totalXpForLevel(5));
        assertEquals(5850, levelingService.totalXpForLevel(10));
    }

    // ---------- computeLevel ----------
    @Test
    public void computeLevel_zeroXp_returnsLevel1() {
        assertEquals(1, levelingService.computeLevel(0));
    }

    @Test
    public void computeLevel_negativeXp_returnsLevel1() {
        assertEquals(1, levelingService.computeLevel(-50));
    }

    @Test
    public void computeLevel_belowFirstThreshold_returnsLevel1() {
        assertEquals(1, levelingService.computeLevel(129));
    }

    @Test
    public void computeLevel_exactlyThreshold_returnsNextLevel() {
        // 130 XP -> level 2
        assertEquals(2, levelingService.computeLevel(130));
        // 390 XP -> level 3
        assertEquals(3, levelingService.computeLevel(390));
        // 5850 XP -> level 10
        assertEquals(10, levelingService.computeLevel(5850));
    }

    @Test
    public void computeLevel_oneBelowNextThreshold_returnsCurrentLevel() {
        // Just below level 3 threshold (390)
        assertEquals(2, levelingService.computeLevel(389));
        // Just below level 5 threshold (1300)
        assertEquals(4, levelingService.computeLevel(1299));
    }

    @Test
    public void computeLevel_largeXp_returnsHighLevel() {
        // Level 20 requires 130 * 20 * 19 / 2 = 24700
        assertEquals(20, levelingService.computeLevel(24700));
        assertEquals(19, levelingService.computeLevel(24699));
    }

    // ---------- coinRewardForLevel ----------
    @Test
    public void coinRewardForLevel_level1OrBelow_returnsZero() {
        assertEquals(0, levelingService.coinRewardForLevel(1));
        assertEquals(0, levelingService.coinRewardForLevel(0));
    }

    @Test
    public void coinRewardForLevel_tier2to5_returns100() {
        assertEquals(100, levelingService.coinRewardForLevel(2));
        assertEquals(100, levelingService.coinRewardForLevel(5));
    }

    @Test
    public void coinRewardForLevel_tier6to10_returns150() {
        assertEquals(150, levelingService.coinRewardForLevel(6));
        assertEquals(150, levelingService.coinRewardForLevel(10));
    }

    @Test
    public void coinRewardForLevel_tier11to15_returns200() {
        assertEquals(200, levelingService.coinRewardForLevel(11));
        assertEquals(200, levelingService.coinRewardForLevel(15));
    }

    @Test
    public void coinRewardForLevel_tier16to20_returns300() {
        assertEquals(300, levelingService.coinRewardForLevel(16));
        assertEquals(300, levelingService.coinRewardForLevel(20));
    }

    @Test
    public void coinRewardForLevel_tier21to30_returns450() {
        assertEquals(450, levelingService.coinRewardForLevel(21));
        assertEquals(450, levelingService.coinRewardForLevel(30));
    }

    @Test
    public void coinRewardForLevel_tier31to40_returns650() {
        assertEquals(650, levelingService.coinRewardForLevel(31));
        assertEquals(650, levelingService.coinRewardForLevel(40));
    }

    @Test
    public void coinRewardForLevel_tier41to50_returns900() {
        assertEquals(900, levelingService.coinRewardForLevel(41));
        assertEquals(900, levelingService.coinRewardForLevel(50));
    }

    @Test
    public void coinRewardForLevel_above50_returns1200() {
        assertEquals(1200, levelingService.coinRewardForLevel(51));
        assertEquals(1200, levelingService.coinRewardForLevel(99));
    }

    // ---------- awardXp ----------
    @Test
    public void awardXp_zeroOrNegative_doesNothing() {
        User user = makeUser(1, 0, 500);
        int gained = levelingService.awardXp(user, 0);

        assertEquals(0, gained);
        verify(userRepository, never()).save(any());
    }

    @Test
    public void awardXp_noLevelUp_persistsXpButNoCoinGain() {
        User user = makeUser(1, 0, 500);
        int gained = levelingService.awardXp(user, 100);

        assertEquals(0, gained);
        assertEquals(100, user.getXp());
        assertEquals(1, user.getLevel());
        assertEquals(500, user.getCoins());
        verify(userRepository).save(user);
    }

    @Test
    public void awardXp_singleLevelUp_awardsTierCoins() {
        User user = makeUser(1, 0, 500);
        // 130 XP exactly hits level 2 boundary
        int gained = levelingService.awardXp(user, 130);

        assertEquals(1, gained);
        assertEquals(2, user.getLevel());
        // Level 2 coin reward: 100
        assertEquals(600, user.getCoins());
    }

    @Test
    public void awardXp_multiLevelUp_accumulatesCoins() {
        User user = makeUser(1, 0, 0);
        // 1300 XP -> level 5; level-ups awarded for 2,3,4,5 = 100+100+100+100 = 400 coins
        int gained = levelingService.awardXp(user, 1300);

        assertEquals(4, gained);
        assertEquals(5, user.getLevel());
        assertEquals(400, user.getCoins());
    }

    @Test
    public void awardXp_legacyUserWithLevelZero_treatedAsLevelOne() {
        User user = makeUser(0, 0, 0);
        int gained = levelingService.awardXp(user, 130);

        assertEquals(1, gained);
        assertEquals(2, user.getLevel());
        assertEquals(100, user.getCoins());
    }

    @Test
    public void awardXp_crossesTierBoundary_awardsDifferentTierCoins() {
        // Start at level 5, award enough to reach level 6 (different tier: 150 coins)
        User user = makeUser(5, 1300, 0);
        // Level 6 needs 130 * 6 * 5 / 2 = 1950 XP. Award 650.
        int gained = levelingService.awardXp(user, 650);

        assertEquals(1, gained);
        assertEquals(6, user.getLevel());
        assertEquals(150, user.getCoins());
    }

    private User makeUser(int level, int xp, int coins) {
        User u = new User();
        u.setId(1L);
        u.setLevel(level);
        u.setXp(xp);
        u.setCoins(coins);
        return u;
    }
}
