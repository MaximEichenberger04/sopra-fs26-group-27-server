package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.entity.UserCosmetic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("userCosmeticRepository")
public interface UserCosmeticRepository extends JpaRepository<UserCosmetic, Long> {

    List<UserCosmetic> findByUserId(Long userId);

    boolean existsByUserIdAndCosmeticId(Long userId, String cosmeticId);

    void deleteByUserId(Long userId);
}
