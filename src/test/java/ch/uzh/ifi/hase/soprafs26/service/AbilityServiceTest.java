package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.AbilityType;
import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.AbilityPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AbilityServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private GameService gameService;
    @Mock private GameStateCache gameStateCache;

    @InjectMocks
    private AbilityService abilityService;

    private User caster;
    private User otherUser;
    private Game game;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        caster = new User();
        caster.setId(1L);
        caster.setToken("caster-token");
        when(userRepository.findByToken("caster-token")).thenReturn(caster);

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setToken("other-token");

        game = new Game();
        game.setId(10L);
        game.setGameStatus(GameStatus.RUNNING);
        game.setChaosMode(true);
        game.setCurrentTurnUserId(1L);
        game.setPlayerIds(List.of(1L, 2L));
        game.setWallsPerPlayer(5);
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameService.buildGameGetDTO(any(Game.class))).thenReturn(new GameGetDTO());
        when(gameService.buildGameGetDTO(any(Game.class), anyLong())).thenReturn(new GameGetDTO());
    }

    // ════════════════════════════════════════════════
    // drawCard
    // ════════════════════════════════════════════════
    @Test
    public void drawCard_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.drawCard(10L, "bad"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    public void drawCard_gameNotFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.drawCard(99L, "caster-token"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void drawCard_outsideChaosMode_throwsBadRequest() {
        game.setChaosMode(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.drawCard(10L, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void drawCard_noPendingDraw_throwsForbidden() {
        when(gameStateCache.hasPendingCardDraw(10L, 1L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.drawCard(10L, "caster-token"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    public void drawCard_validChaosGame_grantsCardAndReturnsDTO() {
        when(gameStateCache.hasPendingCardDraw(10L, 1L)).thenReturn(true);
        // Seed RNG so the test is deterministic
        ReflectionTestUtils.setField(abilityService, "random", new Random(0L));

        GameGetDTO result = abilityService.drawCard(10L, "caster-token");

        assertNotNull(result);
        verify(gameStateCache).grantCard(eq(10L), eq(1L), any(AbilityType.class));
    }

    // ════════════════════════════════════════════════
    // useAbility — guards
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "bad"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_gameNotRunning_throwsBadRequest() {
        game.setGameStatus(GameStatus.ENDED);
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_notChaosMode_throwsForbidden() {
        game.setChaosMode(false);
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_notMyTurn_andNoBonusAction_throwsForbidden() {
        game.setCurrentTurnUserId(2L); // Other player's turn
        when(gameStateCache.hasBonusAction(10L, 1L)).thenReturn(false);
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_cardNotInInventory_throwsForbidden() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        AbilityPostDTO dto = buildDto(AbilityType.FIREBALL, 4, 4, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_consumesBonusActionIfPresent() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.TWO_MOVES));
        when(gameStateCache.hasBonusAction(10L, 1L)).thenReturn(true);
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).consumeBonusAction(10L, 1L);
    }

    // ════════════════════════════════════════════════
    // FIREBALL
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_fireball_missingCoords_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FIREBALL));
        AbilityPostDTO dto = buildDto(AbilityType.FIREBALL, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_fireball_outOfBounds_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FIREBALL));
        AbilityPostDTO dto = buildDto(AbilityType.FIREBALL, 100, 100, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_fireball_removesWallsInRegionAndEndsTurn() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FIREBALL));
        // Wall inside the fireball's 5x5 region around (8,8) (internal coords)
        Wall inRegion = makeWall(8, 8, WallOrientation.HORIZONTAL);
        // Wall outside the region (top-left of board)
        Wall outOfRegion = makeWall(0, 2, WallOrientation.HORIZONTAL);
        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>(List.of(inRegion, outOfRegion)));
        AbilityPostDTO dto = buildDto(AbilityType.FIREBALL, 4, 4, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).removeWall(10L, 8, 8, WallOrientation.HORIZONTAL);
        verify(gameStateCache, never()).removeWall(eq(10L), eq(0), eq(2), any());
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.FIREBALL);
        verify(gameService).advanceTurn(game);
    }

    // ════════════════════════════════════════════════
    // EARTHQUAKE
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_earthquake_alwaysDestroyBranch_removesWall() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.EARTHQUAKE));
        // Force random to always return true → "remove" branch
        Random alwaysTrue = mock(Random.class);
        when(alwaysTrue.nextBoolean()).thenReturn(true);
        ReflectionTestUtils.setField(abilityService, "random", alwaysTrue);

        Wall wall = makeWall(8, 8, WallOrientation.HORIZONTAL);
        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>(List.of(wall)));
        AbilityPostDTO dto = buildDto(AbilityType.EARTHQUAKE, 4, 4, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).removeWall(10L, 8, 8, WallOrientation.HORIZONTAL);
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.EARTHQUAKE);
        verify(gameService).advanceTurn(game);
    }

    @Test
    public void useAbility_earthquake_alwaysNoOp_leavesWall() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.EARTHQUAKE));
        Random alwaysFalse = mock(Random.class);
        when(alwaysFalse.nextBoolean()).thenReturn(false);
        ReflectionTestUtils.setField(abilityService, "random", alwaysFalse);

        Wall wall = makeWall(8, 8, WallOrientation.HORIZONTAL);
        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>(List.of(wall)));
        AbilityPostDTO dto = buildDto(AbilityType.EARTHQUAKE, 4, 4, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache, never()).removeWall(eq(10L), anyInt(), anyInt(), any());
    }

    // ════════════════════════════════════════════════
    // POISON
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_poison_addsPoisonZoneAndEndsTurn() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.POISON));
        AbilityPostDTO dto = buildDto(AbilityType.POISON, 3, 3, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).addPoisonZone(10L, 6, 6); // logical 3,3 → internal 6,6
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.POISON);
        verify(gameService).advanceTurn(game);
    }

    @Test
    public void useAbility_poison_missingCoords_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.POISON));
        AbilityPostDTO dto = buildDto(AbilityType.POISON, null, null, null);

        assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
    }

    // ════════════════════════════════════════════════
    // FREEZE
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_freeze_targetUser_freezesAndGrantsBonusAction() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        when(gameStateCache.getPlayers(10L)).thenReturn(List.of(1L, 2L));
        when(gameStateCache.isFrozen(10L, 2L)).thenReturn(false);
        AbilityPostDTO dto = buildDto(AbilityType.FREEZE, null, null, 2L);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).freezePlayer(10L, 2L);
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.FREEZE);
        verify(gameStateCache).setBonusAction(10L, 1L, 1);
        verify(gameService, never()).advanceTurn(any());
    }

    @Test
    public void useAbility_freeze_missingTargetUser_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        AbilityPostDTO dto = buildDto(AbilityType.FREEZE, null, null, null);

        assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
    }

    @Test
    public void useAbility_freeze_self_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        AbilityPostDTO dto = buildDto(AbilityType.FREEZE, null, null, 1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_freeze_targetNotInGame_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        when(gameStateCache.getPlayers(10L)).thenReturn(List.of(1L, 2L));
        AbilityPostDTO dto = buildDto(AbilityType.FREEZE, null, null, 99L);

        assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
    }

    @Test
    public void useAbility_freeze_targetAlreadyFrozen_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.FREEZE));
        when(gameStateCache.getPlayers(10L)).thenReturn(List.of(1L, 2L));
        when(gameStateCache.isFrozen(10L, 2L)).thenReturn(true);
        AbilityPostDTO dto = buildDto(AbilityType.FREEZE, null, null, 2L);

        assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
    }

    // ════════════════════════════════════════════════
    // PLUS_TWO_WALLS
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_plusTwoWalls_grantsExtraWallsAndBonusAction() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.PLUS_TWO_WALLS));
        when(gameStateCache.getPermanentlyConsumedWalls(10L, 1L)).thenReturn(0);
        when(gameStateCache.getExtraWalls(10L, 1L)).thenReturn(0);
        AbilityPostDTO dto = buildDto(AbilityType.PLUS_TWO_WALLS, null, null, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).addExtraWalls(10L, 1L, 2);
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.PLUS_TWO_WALLS);
        verify(gameStateCache).setBonusAction(10L, 1L, 1);
        verify(gameService, never()).advanceTurn(any());
    }

    @Test
    public void useAbility_plusTwoWalls_atMaxCap_throwsBadRequest() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.PLUS_TWO_WALLS));
        // wallsPerPlayer = 5, max remaining = 7. Already have 7 remaining.
        when(gameStateCache.getPermanentlyConsumedWalls(10L, 1L)).thenReturn(0);
        when(gameStateCache.getExtraWalls(10L, 1L)).thenReturn(2);
        AbilityPostDTO dto = buildDto(AbilityType.PLUS_TWO_WALLS, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> abilityService.useAbility(10L, dto, "caster-token"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void useAbility_plusTwoWalls_nearCap_grantsPartial() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.PLUS_TWO_WALLS));
        // wallsPerPlayer=5, extra=1, consumed=0 → remaining=6, max=7 → gain=1
        when(gameStateCache.getPermanentlyConsumedWalls(10L, 1L)).thenReturn(0);
        when(gameStateCache.getExtraWalls(10L, 1L)).thenReturn(1);
        AbilityPostDTO dto = buildDto(AbilityType.PLUS_TWO_WALLS, null, null, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).addExtraWalls(10L, 1L, 1);
    }

    // ════════════════════════════════════════════════
    // TWO_MOVES
    // ════════════════════════════════════════════════
    @Test
    public void useAbility_twoMoves_grantsTwoBonusActionsAndRemovesCard() {
        when(gameStateCache.getInventory(10L, 1L)).thenReturn(List.of(AbilityType.TWO_MOVES));
        AbilityPostDTO dto = buildDto(AbilityType.TWO_MOVES, null, null, null);

        abilityService.useAbility(10L, dto, "caster-token");

        verify(gameStateCache).setBonusAction(10L, 1L, 2);
        verify(gameStateCache).removeCardFromInventory(10L, 1L, AbilityType.TWO_MOVES);
        verify(gameService, never()).advanceTurn(any());
    }

    // ---------- Helpers ----------
    private AbilityPostDTO buildDto(AbilityType type, Integer row, Integer col, Long target) {
        AbilityPostDTO dto = new AbilityPostDTO();
        dto.setAbilityType(type);
        dto.setTargetRow(row);
        dto.setTargetCol(col);
        dto.setTargetUserId(target);
        return dto;
    }

    private Wall makeWall(int row, int col, WallOrientation orientation) {
        Wall w = new Wall();
        w.setRow(row);
        w.setCol(col);
        w.setOrientation(orientation);
        w.setUserId(1L);
        return w;
    }
}
