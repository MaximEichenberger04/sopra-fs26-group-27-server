package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.MatchHistory;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.MatchHistoryRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MatchHistoryGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserStatisticsGetDTO;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class StatisticsService {

    private final MatchHistoryRepository matchHistoryRepository;
    private final UserRepository userRepository;

    public StatisticsService(
            @Qualifier("matchHistoryRepository") MatchHistoryRepository matchHistoryRepository,
            @Qualifier("userRepository") UserRepository userRepository) {
        this.matchHistoryRepository = matchHistoryRepository;
        this.userRepository = userRepository;
    }

    // ── Authorization helper ────────────────────────────────────────────────────

    /**
     * Statistics are visible to any authenticated user.
     */
    private void assertAuthenticated(String token) {
        User requestingUser = userRepository.findByToken(token);
        if (requestingUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token");
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Returns an aggregated statistics summary for the given user.
     */
    public UserStatisticsGetDTO getStatistics(Long userId, String token) {
        assertAuthenticated(token);

        // Ensure user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<MatchHistory> records = matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(userId);

        int totalGames = records.size();
        int wins = (int) records.stream().filter(MatchHistory::isWon).count();
        int losses = totalGames - wins;

        double winLossRatio = totalGames == 0 ? 0.0 : (double) wins / totalGames;

        // Most frequently played game mode
        String mostPlayedGameMode = records.stream()
                .collect(Collectors.groupingBy(MatchHistory::getGameMode, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        UserStatisticsGetDTO dto = new UserStatisticsGetDTO();
        dto.setTotalGames(totalGames);
        dto.setWins(wins);
        dto.setLosses(losses);
        dto.setWinLossRatio(winLossRatio);
        dto.setMostPlayedGameMode(mostPlayedGameMode);
        return dto;
    }

    /**
     * Returns the full match history for the given user (newest first).
     */
    public List<MatchHistoryGetDTO> getMatchHistory(Long userId, String token) {
        assertAuthenticated(token);

        // Ensure user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Mapper ──────────────────────────────────────────────────────────────────

    private MatchHistoryGetDTO toDTO(MatchHistory m) {
        MatchHistoryGetDTO dto = new MatchHistoryGetDTO();
        dto.setId(m.getId());
        dto.setGameId(m.getGameId());
        dto.setOpponentUsernames(m.getOpponentUsernames());
        dto.setGameMode(m.getGameMode());
        dto.setWon(m.isWon());
        dto.setPlayedAt(m.getPlayedAt());
        return dto;
    }
}
