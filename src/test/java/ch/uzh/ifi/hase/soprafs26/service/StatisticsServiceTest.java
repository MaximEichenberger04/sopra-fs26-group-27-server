package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.MatchHistory;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.MatchHistoryRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MatchHistoryGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserStatisticsGetDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StatisticsServiceTest {

    @Mock
    private MatchHistoryRepository matchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    private User authenticatedUser;
    private static final String VALID_TOKEN = "valid-token-abc";
    private static final Long USER_ID = 1L;
    private static final Long GAME_ID = 100L;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        authenticatedUser = new User();
        authenticatedUser.setId(USER_ID);
        authenticatedUser.setUsername("testUser");
        authenticatedUser.setToken(VALID_TOKEN);

        // Default: token resolves to authenticated user
        when(userRepository.findByToken(VALID_TOKEN)).thenReturn(authenticatedUser);
        // Default: userId resolves to existing user
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(authenticatedUser));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private MatchHistory buildMatch(boolean won, String gameMode, int xpEarned, LocalDateTime playedAt) {
        MatchHistory m = new MatchHistory();
        m.setUserId(USER_ID);
        m.setGameId(GAME_ID);
        m.setOpponentUsernames("opponent1");
        m.setGameMode(gameMode);
        m.setWon(won);
        m.setXpEarned(xpEarned);
        m.setPlayedAt(playedAt);
        return m;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getStatistics – happy path
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getStatistics_noGamesPlayed_returnsZerosAndNullMode() {
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(0, dto.getTotalGames());
        assertEquals(0, dto.getWins());
        assertEquals(0, dto.getLosses());
        assertEquals(0.0, dto.getWinLossRatio(), 0.001);
        assertNull(dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_allWins_ratioIsOne() {
        List<MatchHistory> records = List.of(
                buildMatch(true, "CLASSIC", 50, LocalDateTime.now()),
                buildMatch(true, "CLASSIC", 60, LocalDateTime.now())
        );
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID)).thenReturn(records);

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(2, dto.getTotalGames());
        assertEquals(2, dto.getWins());
        assertEquals(0, dto.getLosses());
        assertEquals(1.0, dto.getWinLossRatio(), 0.001);
        assertEquals("CLASSIC", dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_allLosses_ratioIsZero() {
        List<MatchHistory> records = List.of(
                buildMatch(false, "CHAOS", 10, LocalDateTime.now()),
                buildMatch(false, "CHAOS", 10, LocalDateTime.now())
        );
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID)).thenReturn(records);

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(2, dto.getTotalGames());
        assertEquals(0, dto.getWins());
        assertEquals(2, dto.getLosses());
        assertEquals(0.0, dto.getWinLossRatio(), 0.001);
        assertEquals("CHAOS", dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_mixedResults_correctRatioAndCounts() {
        List<MatchHistory> records = List.of(
                buildMatch(true, "CLASSIC", 100, LocalDateTime.now()),
                buildMatch(false, "CLASSIC", 20, LocalDateTime.now()),
                buildMatch(true, "CLASSIC", 80, LocalDateTime.now()),
                buildMatch(false, "CHAOS", 15, LocalDateTime.now())
        );
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID)).thenReturn(records);

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(4, dto.getTotalGames());
        assertEquals(2, dto.getWins());
        assertEquals(2, dto.getLosses());
        assertEquals(0.5, dto.getWinLossRatio(), 0.001);
        // CLASSIC appears 3x, CHAOS 1x
        assertEquals("CLASSIC", dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_singleGame_winRatioCorrect() {
        List<MatchHistory> records = List.of(
                buildMatch(true, "CLASSIC", 75, LocalDateTime.now())
        );
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID)).thenReturn(records);

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(1, dto.getTotalGames());
        assertEquals(1, dto.getWins());
        assertEquals(0, dto.getLosses());
        assertEquals(1.0, dto.getWinLossRatio(), 0.001);
        assertEquals("CLASSIC", dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_multipleModes_mostPlayedIsCorrect() {
        List<MatchHistory> records = List.of(
                buildMatch(true, "CHAOS", 50, LocalDateTime.now()),
                buildMatch(false, "CHAOS", 10, LocalDateTime.now()),
                buildMatch(true, "CLASSIC", 80, LocalDateTime.now())
        );
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID)).thenReturn(records);

        UserStatisticsGetDTO dto = statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        assertEquals(3, dto.getTotalGames());
        // CHAOS has 2 games vs CLASSIC's 1
        assertEquals("CHAOS", dto.getMostPlayedGameMode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getStatistics – error cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getStatistics_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getStatistics(USER_ID, "bad-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    public void getStatistics_nullToken_throwsUnauthorized() {
        when(userRepository.findByToken(null)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getStatistics(USER_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    public void getStatistics_userNotFound_throwsNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getStatistics(999L, VALID_TOKEN));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getMatchHistory – happy path
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getMatchHistory_noGames_returnsEmptyList() {
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());

        List<MatchHistoryGetDTO> result = statisticsService.getMatchHistory(USER_ID, VALID_TOKEN);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getMatchHistory_multipleRecords_allFieldsMapped() {
        LocalDateTime time1 = LocalDateTime.of(2026, 5, 10, 12, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 5, 9, 18, 30);

        MatchHistory m1 = buildMatch(true, "CLASSIC", 120, time1);
        m1.setOpponentUsernames("alice,bob");
        MatchHistory m2 = buildMatch(false, "CHAOS", 30, time2);
        m2.setOpponentUsernames("charlie");

        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(List.of(m1, m2));

        List<MatchHistoryGetDTO> result = statisticsService.getMatchHistory(USER_ID, VALID_TOKEN);

        assertEquals(2, result.size());

        MatchHistoryGetDTO dto1 = result.get(0);
        assertEquals(USER_ID, dto1.getUserId());
        assertEquals(GAME_ID, dto1.getGameId());
        assertEquals("alice,bob", dto1.getOpponentUsernames());
        assertEquals("CLASSIC", dto1.getGameMode());
        assertTrue(dto1.isWon());
        assertEquals(120, dto1.getXpEarned());
        assertEquals(time1, dto1.getPlayedAt());

        MatchHistoryGetDTO dto2 = result.get(1);
        assertEquals("charlie", dto2.getOpponentUsernames());
        assertEquals("CHAOS", dto2.getGameMode());
        assertFalse(dto2.isWon());
        assertEquals(30, dto2.getXpEarned());
        assertEquals(time2, dto2.getPlayedAt());
    }

    @Test
    public void getMatchHistory_singleWin_correctDTO() {
        LocalDateTime now = LocalDateTime.now();
        MatchHistory m = buildMatch(true, "CLASSIC", 75, now);
        m.setOpponentUsernames("opponent1,opponent2");

        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(List.of(m));

        List<MatchHistoryGetDTO> result = statisticsService.getMatchHistory(USER_ID, VALID_TOKEN);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isWon());
        assertEquals("opponent1,opponent2", result.get(0).getOpponentUsernames());
    }

    @Test
    public void getMatchHistory_xpEarnedIsZero_stillMapped() {
        MatchHistory m = buildMatch(false, "CLASSIC", 0, LocalDateTime.now());
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(List.of(m));

        List<MatchHistoryGetDTO> result = statisticsService.getMatchHistory(USER_ID, VALID_TOKEN);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getXpEarned());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getMatchHistory – error cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getMatchHistory_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("wrong-token")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getMatchHistory(USER_ID, "wrong-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    public void getMatchHistory_userNotFound_throwsNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getMatchHistory(404L, VALID_TOKEN));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getGameResults
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getGameResults_validGame_returnsAllPlayerRecords() {
        MatchHistory m1 = buildMatch(true, "CLASSIC", 100, LocalDateTime.now());
        m1.setUserId(1L);
        MatchHistory m2 = buildMatch(false, "CLASSIC", 25, LocalDateTime.now());
        m2.setUserId(2L);

        when(matchHistoryRepository.findByGameId(GAME_ID)).thenReturn(List.of(m1, m2));

        List<MatchHistoryGetDTO> result = statisticsService.getGameResults(GAME_ID, VALID_TOKEN);

        assertEquals(2, result.size());
        // One winner, one loser
        long winners = result.stream().filter(MatchHistoryGetDTO::isWon).count();
        assertEquals(1, winners);
    }

    @Test
    public void getGameResults_noRecordsForGame_returnsEmptyList() {
        when(matchHistoryRepository.findByGameId(999L)).thenReturn(Collections.emptyList());

        List<MatchHistoryGetDTO> result = statisticsService.getGameResults(999L, VALID_TOKEN);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getGameResults_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getGameResults(GAME_ID, "bad"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Repository interaction verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getStatistics_verifiesTokenAndUserLookup() {
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());

        statisticsService.getStatistics(USER_ID, VALID_TOKEN);

        verify(userRepository, times(1)).findByToken(VALID_TOKEN);
        verify(userRepository, times(1)).findById(USER_ID);
        verify(matchHistoryRepository, times(1)).findByUserIdOrderByPlayedAtDesc(USER_ID);
    }

    @Test
    public void getMatchHistory_verifiesTokenAndUserLookup() {
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(USER_ID))
                .thenReturn(Collections.emptyList());

        statisticsService.getMatchHistory(USER_ID, VALID_TOKEN);

        verify(userRepository, times(1)).findByToken(VALID_TOKEN);
        verify(userRepository, times(1)).findById(USER_ID);
        verify(matchHistoryRepository, times(1)).findByUserIdOrderByPlayedAtDesc(USER_ID);
    }
}