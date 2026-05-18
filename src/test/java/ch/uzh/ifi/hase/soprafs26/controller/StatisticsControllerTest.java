package ch.uzh.ifi.hase.soprafs26.controller;

import tools.jackson.databind.ObjectMapper;

import ch.uzh.ifi.hase.soprafs26.rest.dto.MatchHistoryGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserStatisticsGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.StatisticsService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-level tests for the match-history and user-statistics endpoints.
 * Uses @WebMvcTest – no real Spring context, StatisticsService is mocked.
 */
@WebMvcTest(UserController.class)
public class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StatisticsService statisticsService;

    private static final String VALID_TOKEN = "valid-token-123";

    // ─── helpers ────────────────────────────────────────────────────────────────

    private UserStatisticsGetDTO buildStats(int total, int wins, double ratio, String mode) {
        UserStatisticsGetDTO dto = new UserStatisticsGetDTO();
        dto.setTotalGames(total);
        dto.setWins(wins);
        dto.setLosses(total - wins);
        dto.setWinLossRatio(ratio);
        dto.setMostPlayedGameMode(mode);
        return dto;
    }

    private MatchHistoryGetDTO buildMatchDTO(Long id, boolean won, String gameMode, int xp) {
        MatchHistoryGetDTO dto = new MatchHistoryGetDTO();
        dto.setId(id);
        dto.setUserId(1L);
        dto.setGameId(100L);
        dto.setOpponentUsernames("opponent1");
        dto.setGameMode(gameMode);
        dto.setWon(won);
        dto.setXpEarned(xp);
        dto.setPlayedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /users/{id}/statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getStatistics_validRequest_returns200WithBody() throws Exception {
        UserStatisticsGetDTO stats = buildStats(10, 6, 0.6, "CLASSIC");
        given(statisticsService.getStatistics(1L, VALID_TOKEN)).willReturn(stats);

        mockMvc.perform(get("/users/1/statistics")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames", is(10)))
                .andExpect(jsonPath("$.wins", is(6)))
                .andExpect(jsonPath("$.losses", is(4)))
                .andExpect(jsonPath("$.winLossRatio", is(0.6)))
                .andExpect(jsonPath("$.mostPlayedGameMode", is("CLASSIC")));
    }

    @Test
    public void getStatistics_noGamesPlayed_returnsZerosAndNullMode() throws Exception {
        UserStatisticsGetDTO stats = buildStats(0, 0, 0.0, null);
        given(statisticsService.getStatistics(1L, VALID_TOKEN)).willReturn(stats);

        mockMvc.perform(get("/users/1/statistics")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames", is(0)))
                .andExpect(jsonPath("$.wins", is(0)))
                .andExpect(jsonPath("$.losses", is(0)))
                .andExpect(jsonPath("$.winLossRatio", is(0.0)))
                .andExpect(jsonPath("$.mostPlayedGameMode").doesNotExist());
    }

    @Test
    public void getStatistics_allWins_ratioOne() throws Exception {
        UserStatisticsGetDTO stats = buildStats(5, 5, 1.0, "CHAOS");
        given(statisticsService.getStatistics(1L, VALID_TOKEN)).willReturn(stats);

        mockMvc.perform(get("/users/1/statistics")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins", is(5)))
                .andExpect(jsonPath("$.losses", is(0)))
                .andExpect(jsonPath("$.winLossRatio", is(1.0)));
    }

    @Test
    public void getStatistics_invalidToken_returns401() throws Exception {
        given(statisticsService.getStatistics(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));

        mockMvc.perform(get("/users/1/statistics")
                        .header("Authorization", "bad-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void getStatistics_missingToken_returns401() throws Exception {
        given(statisticsService.getStatistics(anyLong(), isNull()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));

        mockMvc.perform(get("/users/1/statistics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void getStatistics_userNotFound_returns404() throws Exception {
        given(statisticsService.getStatistics(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        mockMvc.perform(get("/users/999/statistics")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getStatistics_responseContainsAllExpectedFields() throws Exception {
        UserStatisticsGetDTO stats = buildStats(3, 2, 0.667, "CLASSIC");
        given(statisticsService.getStatistics(1L, VALID_TOKEN)).willReturn(stats);

        mockMvc.perform(get("/users/1/statistics")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames").exists())
                .andExpect(jsonPath("$.wins").exists())
                .andExpect(jsonPath("$.losses").exists())
                .andExpect(jsonPath("$.winLossRatio").exists())
                .andExpect(jsonPath("$.mostPlayedGameMode").exists());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /users/{id}/match-history
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    public void getMatchHistory_validRequest_returns200WithList() throws Exception {
        List<MatchHistoryGetDTO> history = List.of(
                buildMatchDTO(1L, true, "CLASSIC", 100),
                buildMatchDTO(2L, false, "CHAOS", 20)
        );
        given(statisticsService.getMatchHistory(1L, VALID_TOKEN)).willReturn(history);

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].won", is(true)))
                .andExpect(jsonPath("$[0].gameMode", is("CLASSIC")))
                .andExpect(jsonPath("$[0].xpEarned", is(100)))
                .andExpect(jsonPath("$[1].won", is(false)))
                .andExpect(jsonPath("$[1].gameMode", is("CHAOS")))
                .andExpect(jsonPath("$[1].xpEarned", is(20)));
    }

    @Test
    public void getMatchHistory_noGames_returnsEmptyArray() throws Exception {
        given(statisticsService.getMatchHistory(1L, VALID_TOKEN)).willReturn(Collections.emptyList());

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getMatchHistory_singleRecord_allFieldsPresent() throws Exception {
        MatchHistoryGetDTO dto = buildMatchDTO(42L, true, "CLASSIC", 85);
        dto.setOpponentUsernames("alice,bob");

        given(statisticsService.getMatchHistory(1L, VALID_TOKEN)).willReturn(List.of(dto));

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(42)))
                .andExpect(jsonPath("$[0].userId", is(1)))
                .andExpect(jsonPath("$[0].gameId", is(100)))
                .andExpect(jsonPath("$[0].opponentUsernames", is("alice,bob")))
                .andExpect(jsonPath("$[0].gameMode", is("CLASSIC")))
                .andExpect(jsonPath("$[0].won", is(true)))
                .andExpect(jsonPath("$[0].xpEarned", is(85)))
                .andExpect(jsonPath("$[0].playedAt").exists());
    }

    @Test
    public void getMatchHistory_invalidToken_returns401() throws Exception {
        given(statisticsService.getMatchHistory(anyLong(), eq("wrong-token")))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token"));

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void getMatchHistory_userNotFound_returns404() throws Exception {
        given(statisticsService.getMatchHistory(anyLong(), anyString()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        mockMvc.perform(get("/users/999/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getMatchHistory_largeHistory_returnsAllRecords() throws Exception {
        List<MatchHistoryGetDTO> history = List.of(
                buildMatchDTO(1L, true, "CLASSIC", 100),
                buildMatchDTO(2L, false, "CHAOS", 25),
                buildMatchDTO(3L, true, "CLASSIC", 90),
                buildMatchDTO(4L, true, "CHAOS", 110),
                buildMatchDTO(5L, false, "CLASSIC", 15)
        );
        given(statisticsService.getMatchHistory(1L, VALID_TOKEN)).willReturn(history);

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    public void getMatchHistory_xpEarnedIsZero_fieldStillReturned() throws Exception {
        MatchHistoryGetDTO dto = buildMatchDTO(1L, false, "CLASSIC", 0);
        given(statisticsService.getMatchHistory(1L, VALID_TOKEN)).willReturn(List.of(dto));

        mockMvc.perform(get("/users/1/match-history")
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].xpEarned", is(0)));
    }
}