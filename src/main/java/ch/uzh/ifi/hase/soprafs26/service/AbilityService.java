package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.AbilityPostDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles all chaos-mode ability card logic.
 *
 * Responsibilities:
 *   - Drawing cards from the deck into a player's inventory
 *   - Validating and dispatching ability use to per-ability private methods
 *   - Mutating GameStateCache for positional effects (Fireball, Earthquake, Poison)
 *   - Setting flags in GameStateCache for turn-modifying effects (Freeze, +2 Walls, 2 Moves)
 *   - Removing the used card from the player's inventory
 *
 * Turn flow contract (what AbilityService guarantees vs. what MoveService handles):
 *   - If an ability ENDS the turn → AbilityService calls gameService.advanceTurn()
 *   - If an ability GRANTS an extra action → AbilityService sets a flag in GameStateCache;
 *     MoveService reads the flag on the next move/wall/ability to skip advanceTurn()
 *
 * Abilities that end turn:   FIREBALL, EARTHQUAKE, POISON
 * Abilities that grant extra action: FREEZE, PLUS_TWO_WALLS, TWO_MOVES
 */
@Service
@Transactional
public class AbilityService {

    private static final int INTERNAL_SIZE = 17;
    private static final int WALL_CAP = 10;

    private static final Object[][] CARD_WEIGHTS = { // weighted probabilities 
        { AbilityType.PLUS_TWO_WALLS, 20 },
        { AbilityType.TWO_MOVES,      20 },
        { AbilityType.FIREBALL,       15 },
        { AbilityType.FREEZE,         15 },
        { AbilityType.EARTHQUAKE,     15 },
        { AbilityType.POISON,         15 },
    };

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameService gameService;
    private final GameStateCache gameStateCache;
    private final Random random = new Random();

    public AbilityService(
            @Qualifier("gameRepository") GameRepository gameRepository,
            @Qualifier("userRepository") UserRepository userRepository,
            GameService gameService,
            GameStateCache gameStateCache) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.gameService    = gameService;
        this.gameStateCache = gameStateCache;
    }

    public GameGetDTO drawCard(Long gameId, String token) {
        User user = requireUser(token);
        Game game = requireGame(gameId);
        if(!game.isChaosMode()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ability cards are only available in chaos mode");
        }
        abilityType card = rollWeightedCard();
        gameStateCache.grantCard(gameId, user.getId(), card); // give card to inventory
        return gameService.buildGameGetDTO(game);
    }

    // private helper method used by drawCard to pick a random card
    private AbilityType rollWeightedCard() {
        int totalWeight = 0;
        for(Object[] entry : CARD_WEIGHTS) {
            totalWeight += (int) entry[1]; // total sum weights
        }
        int roll = random.nextInt(totalWeight); // get random int (0-99)
        for(Object[] entry: CARD_WEIGHTS) {
            roll -= (int) entry[1]; // remove weight from roll
            if (roll < 0) { // if roll becomes negative, we take this ability card
                return (AbilityType) entry[0];
            }
        }
    }

    public GameGetDTO useAbility(Long gameId, AbilityPostDTO dto, String token) {
        User user = requireUser(token);
        Long userId = user.getId();
        Game game = requireGame(gameId);
        requireTurnOrBonusAction(game, userId);
        requireCardInInventory(gameId, userId, dto.getAbilityType());

        switch (dto.getAbilityType()) { // check for each ability what we need to do
            case FIREBALL:
                requireTargetCoords(dto);
                applyFireball(gameId, dto.getTargetRow(), dto.getTargetCol());
                break;
            case EARTHQUAKE:
                requireTargetCoords(dto);
                applyEarthquake(gameId, dto.getTargetRow(), dto.getTargetCol());
                break;
            case FREEZE:
                requireTargetUser(dto);
                applyFreeze(gameId, dto.getTargetUserId());
                break;
            case POISON:
                requireTargetCoords(dto);
                applyPoison(gameId, dto.getTargetRow(), dto.getTargetCol());
                break;
            case PLUS_TWO_WALLS:
                applyPlusTwoWalls(gameId, userId);
                break;
            case TWO_MOVES:
                applyTwoMoves(gameId, userId);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown ability type: " + dto.getAbilityType());
        }
    }

    private void applyFireball(Long gameId, int targetRow, int targetCol) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void applyEarthquake(Long gameId, int targetRow, int targetCol) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void applyFreeze(Long gameId, Long casterUserId, Long targetUserId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void applyPoison(Long gameId, int targetRow, int targetCol) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void applyPlusTwoWalls(Long gameId, Long userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void applyTwoMoves(Long gameId, Long userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // Guards for validation logic
    private User requireUser(String token) {
        // TODO: userRepository.findByToken(token), throw 401 if null
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private Game requireGame(Long gameId) {
        // TODO: gameRepository.findById(gameId).orElseThrow(404)
        // TODO: check gameStatus == RUNNING
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void requireTurnOrBonusAction(Game game, Long userId) {
        // TODO: check game.isChaosMode()
        // TODO: check currentTurnUserId == userId OR gameStateCache.hasBonusAction(gameId, userId)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void requireCardInInventory(Long gameId, Long userId, AbilityType type) {
        // TODO: gameStateCache.getInventory(gameId, userId).contains(type)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void requireTargetCoords(AbilityPostDTO dto) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
 
    private void requireTargetUser(AbilityPostDTO dto) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
 
    private void validateBoardCoord(int row, int col, String ability) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}