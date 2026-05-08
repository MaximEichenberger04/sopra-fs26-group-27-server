package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.entity.MatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("matchHistoryRepository")
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

    /** All match records for a specific user, newest first. */
    List<MatchHistory> findByUserIdOrderByPlayedAtDesc(Long userId);
}