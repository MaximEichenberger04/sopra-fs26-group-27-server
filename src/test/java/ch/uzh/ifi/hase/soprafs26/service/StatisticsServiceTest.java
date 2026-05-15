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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class StatisticsServiceTest {

    @Mock
    private MatchHistoryRepository matchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    private User authedUser;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        authedUser = new User();
        authedUser.setId(1L);
        authedUser.setToken("valid-token");
        when(userRepository.findByToken("valid-token")).thenReturn(authedUser);
    }

    // ---------- getStatistics ----------
    @Test
    public void getStatistics_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getStatistics(1L, "bad"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    public void getStatistics_unknownUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getStatistics(99L, "valid-token"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getStatistics_noMatches_returnsZeroesAndNullMode() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(authedUser));
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(1L)).thenReturn(Collections.emptyList());

        UserStatisticsGetDTO dto = statisticsService.getStatistics(1L, "valid-token");

        assertEquals(0, dto.getTotalGames());
        assertEquals(0, dto.getWins());
        assertEquals(0, dto.getLosses());
        assertEquals(0.0, dto.getWinLossRatio());
        assertNull(dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_mixedHistory_computesWinsLossesAndMode() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(authedUser));
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(1L)).thenReturn(List.of(
                match(1L, 1L, "CLASSIC", true),
                match(2L, 1L, "CHAOS", false),
                match(3L, 1L, "CHAOS", true),
                match(4L, 1L, "CHAOS", false)
        ));

        UserStatisticsGetDTO dto = statisticsService.getStatistics(1L, "valid-token");

        assertEquals(4, dto.getTotalGames());
        assertEquals(2, dto.getWins());
        assertEquals(2, dto.getLosses());
        assertEquals(0.5, dto.getWinLossRatio());
        assertEquals("CHAOS", dto.getMostPlayedGameMode());
    }

    @Test
    public void getStatistics_allWins_winLossRatio1() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(authedUser));
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(1L)).thenReturn(List.of(
                match(1L, 1L, "CLASSIC", true),
                match(2L, 1L, "CLASSIC", true)
        ));

        UserStatisticsGetDTO dto = statisticsService.getStatistics(1L, "valid-token");

        assertEquals(2, dto.getWins());
        assertEquals(0, dto.getLosses());
        assertEquals(1.0, dto.getWinLossRatio());
    }

    // ---------- getMatchHistory ----------
    @Test
    public void getMatchHistory_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> statisticsService.getMatchHistory(1L, "bad"));
    }

    @Test
    public void getMatchHistory_unknownUser_throwsNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> statisticsService.getMatchHistory(42L, "valid-token"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getMatchHistory_returnsMappedRecords() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(authedUser));
        MatchHistory m = match(7L, 1L, "CLASSIC", true);
        m.setOpponentUsernames("opp1,opp2");
        m.setXpEarned(120);
        when(matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(1L)).thenReturn(List.of(m));

        List<MatchHistoryGetDTO> result = statisticsService.getMatchHistory(1L, "valid-token");

        assertEquals(1, result.size());
        MatchHistoryGetDTO dto = result.get(0);
        assertEquals(7L, dto.getId());
        assertEquals(1L, dto.getUserId());
        assertEquals("opp1,opp2", dto.getOpponentUsernames());
        assertEquals("CLASSIC", dto.getGameMode());
        assertTrue(dto.isWon());
        assertEquals(120, dto.getXpEarned());
    }

    // ---------- getGameResults ----------
    @Test
    public void getGameResults_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> statisticsService.getGameResults(5L, "bad"));
    }

    @Test
    public void getGameResults_returnsAllPlayersForGame() {
        when(matchHistoryRepository.findByGameId(5L)).thenReturn(List.of(
                match(10L, 1L, "CHAOS", true),
                match(11L, 2L, "CHAOS", false),
                match(12L, 3L, "CHAOS", false)
        ));

        List<MatchHistoryGetDTO> results = statisticsService.getGameResults(5L, "valid-token");

        assertEquals(3, results.size());
        assertEquals(1L, results.get(0).getUserId());
        assertTrue(results.get(0).isWon());
    }

    @Test
    public void getGameResults_emptyGame_returnsEmptyList() {
        when(matchHistoryRepository.findByGameId(99L)).thenReturn(Collections.emptyList());

        List<MatchHistoryGetDTO> results = statisticsService.getGameResults(99L, "valid-token");

        assertTrue(results.isEmpty());
    }

    private MatchHistory match(Long id, Long userId, String mode, boolean won) {
        MatchHistory m = new MatchHistory();
        m.setId(id);
        m.setUserId(userId);
        m.setGameId(1L);
        m.setOpponentUsernames("opp");
        m.setGameMode(mode);
        m.setWon(won);
        m.setPlayedAt(LocalDateTime.now());
        m.setXpEarned(30);
        return m;
    }
}
