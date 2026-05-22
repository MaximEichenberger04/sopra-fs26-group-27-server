package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MoveServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameService gameService;

    @Mock
    private GameStateCache gameStateCache;

    @InjectMocks
    private MoveService moveService;

    private User testUser;
    private User otherUser;
    private Game testGame;
    private GameGetDTO gameGetDTO;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(1L);
        testUser.setToken("valid-token");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setToken("other-token");

        testGame = new Game();
        testGame.setId(10L);
        testGame.setGameStatus(GameStatus.RUNNING);
        testGame.setPlayerIds(Arrays.asList(1L, 2L));
        testGame.setActivePlayerIds(Arrays.asList(1L, 2L));
        testGame.setCurrentTurnUserId(1L);
        testGame.setWallsPerPlayer(10);
        testGame.setSizeBoard(9);
        testGame.setMapTheme("Classic");

        gameGetDTO = new GameGetDTO();
        gameGetDTO.setId(10L);
        gameGetDTO.setGameStatus(GameStatus.RUNNING);

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(userRepository.findByToken("other-token")).thenReturn(otherUser);
        when(gameRepository.findById(10L)).thenReturn(Optional.of(testGame));
        when(gameService.buildGameGetDTO(testGame)).thenReturn(gameGetDTO);
    }

    @Test
    public void hasPathToGoalRow_emptyBoard_returnsTrue() {
        boolean[][] grid = new boolean[17][17];

        boolean result = moveService.hasPathToGoalRow(grid, 16, 8, 0);

        assertTrue(result);
    }

    @Test
    public void hasPathToGoalRow_fullHorizontalBarrier_returnsFalse() {
        boolean[][] grid = new boolean[17][17];

        for (int col = 0; col < 17; col += 2) {
            grid[15][col] = true;
        }

        boolean result = moveService.hasPathToGoalRow(grid, 16, 8, 0);

        assertFalse(result);
    }

    @Test
    public void hasPathToGoalCol_fullVerticalBarrier_returnsFalse() {
        boolean[][] grid = new boolean[17][17];

        for (int row = 0; row < 17; row += 2) {
            grid[row][1] = true;
        }

        boolean result = moveService.hasPathToGoalCol(grid, 8, 0, 16);

        assertFalse(result);
    }

    @Test
    public void processMove_invalidToken_throwsUnauthorized() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        when(userRepository.findByToken("bad-token")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "bad-token")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    public void processMove_notUsersTurn_throwsForbidden() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        testGame.setCurrentTurnUserId(2L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    public void processMove_invalidTargetField_throwsBadRequest() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14});

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void processMove_validSimpleMove_movesPawnAndAdvancesTurn() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        Pawn currentPawn = pawn(1L, 16, 8);
        Pawn opponentPawn = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(currentPawn, opponentPawn));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(currentPawn);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(false);

        GameGetDTO result = moveService.processMove(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).movePawn(10L, 1L, 14, 8);
        verify(gameService).advanceTurn(testGame);
        verify(gameService).buildGameGetDTO(testGame);
        verify(gameService, never()).endGame(any(), any());
    }

    @Test
    public void processMove_winningMove_endsGame() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {0, 8});

        Pawn currentPawn = pawn(1L, 2, 8);
        Pawn opponentPawn = pawn(2L, 16, 8);

        GameGetDTO endedGame = new GameGetDTO();
        endedGame.setId(10L);
        endedGame.setGameStatus(GameStatus.ENDED);
        endedGame.setWinnerId(1L);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(currentPawn, opponentPawn));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(currentPawn);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(true);
        when(gameService.endGame(testGame, 1L)).thenReturn(endedGame);

        GameGetDTO result = moveService.processMove(10L, dto, "valid-token");

        assertEquals(endedGame, result);
        verify(gameStateCache).movePawn(10L, 1L, 0, 8);
        verify(gameService).endGame(testGame, 1L);
        verify(gameService, never()).advanceTurn(any());
    }

    @Test
    public void processMove_moveBlockedByWall_throwsBadRequest() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        Pawn currentPawn = pawn(1L, 16, 8);
        Pawn opponentPawn = pawn(2L, 0, 8);

        boolean[][] grid = new boolean[17][17];
        grid[15][8] = true;

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(currentPawn, opponentPawn));
        when(gameStateCache.getWallGrid(10L)).thenReturn(grid);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(currentPawn);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(gameStateCache, never()).movePawn(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void applyWallPlacement_missingOrientation_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void applyWallPlacement_invalidWallCenter_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {8, 8});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void applyWallPlacement_noWallsRemaining_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        List<Wall> walls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Wall wall = new Wall();
            wall.setUserId(1L);
            wall.setRow(1);
            wall.setCol(1 + (i % 8) * 2);
            wall.setOrientation(WallOrientation.HORIZONTAL);
            walls.add(wall);
        }

        when(gameStateCache.getWalls(10L)).thenReturn(walls);
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8),
                pawn(2L, 0, 8)
        ));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPermanentlyConsumedWalls(10L, 1L)).thenReturn(10);
        when(gameStateCache.getExtraWalls(10L, 1L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    public void applyWallPlacement_overlappingWall_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        boolean[][] grid = new boolean[17][17];
        grid[7][7] = true;

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8),
                pawn(2L, 0, 8)
        ));
        when(gameStateCache.getWallGrid(10L)).thenReturn(grid);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(gameStateCache, never()).placeWall(anyLong(), anyInt(), anyInt(), any(), anyLong());
    }

    @Test
    public void applyWallPlacement_blocksAllPaths_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {15, 15});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        boolean[][] grid = new boolean[17][17];

        // Block all crossing points on row 15 except the last section
        for (int col = 0; col <= 12; col++) {
            grid[15][col] = true;
        }

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8),
                pawn(2L, 0, 8)
        ));
        when(gameStateCache.getWallGrid(10L)).thenReturn(grid);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(gameStateCache, never()).placeWall(anyLong(), anyInt(), anyInt(), any(), anyLong());
    }

    @Test
    public void applyWallPlacement_validPlacement_placesWallAndAdvancesTurn() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8),
                pawn(2L, 0, 8)
        ));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);

        GameGetDTO result = moveService.applyWallPlacement(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).placeWall(10L, 7, 7, WallOrientation.HORIZONTAL, 1L);
        verify(gameService).advanceTurn(testGame);
        verify(gameService).buildGameGetDTO(testGame);
    }

    // ════════════════════════════════════════════════
    // Additional processMove tests
    // ════════════════════════════════════════════════
    @Test
    public void processMove_gameNotFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {0, 0});

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(99L, dto, "valid-token"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    public void processMove_gameNotRunning_throwsBadRequest() {
        testGame.setGameStatus(GameStatus.ENDED);
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void processMove_inactivePlayer_throwsForbidden() {
        testGame.setActivePlayerIds(Arrays.asList(2L)); // user 1 was eliminated
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void processMove_frozenPlayer_throwsForbidden() {
        when(gameStateCache.isFrozen(10L, 1L)).thenReturn(true);
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void processMove_pawnNotFound_throwsNotFound() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(pawn(2L, 0, 8)));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    public void processMove_poisonedCell_throwsBadRequest() {
        testGame.setChaosMode(true);
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        Pawn current = pawn(1L, 16, 8);
        Pawn opponent = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);
        when(gameStateCache.isPoisoned(10L, 14, 8)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void processMove_jumpOverOpponent_succeeds() {
        // Current pawn at (8,8), opponent at (10,8) → jump to (12,8)
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {12, 8});

        Pawn current = pawn(1L, 8, 8);
        Pawn opponent = pawn(2L, 10, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(false);

        GameGetDTO result = moveService.processMove(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).movePawn(10L, 1L, 12, 8);
    }

    @Test
    public void processMove_diagonalMove_whenStraightJumpBlocked_succeeds() {
        // current at (8,8), opponent at (10,8). Wall behind opponent at (11,8).
        // Straight jump blocked → diagonal to (10,6) or (10,10) is allowed.
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {10, 10});

        Pawn current = pawn(1L, 8, 8);
        Pawn opponent = pawn(2L, 10, 8);

        boolean[][] grid = new boolean[17][17];
        grid[11][8] = true; // wall behind opponent

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(grid);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(false);

        GameGetDTO result = moveService.processMove(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).movePawn(10L, 1L, 10, 10);
    }

    @Test
    public void processMove_toCellOccupiedByOpponent_throwsBadRequest() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {0, 8}); // opponent's cell

        Pawn current = pawn(1L, 16, 8);
        Pawn opponent = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);

        // (16,8) -> (0,8) is not adjacent; falls through to invalid move
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void processMove_oddTargetCell_throwsBadRequest() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {15, 8}); // odd row = invalid pawn cell

        Pawn current = pawn(1L, 16, 8);
        Pawn opponent = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.processMove(10L, dto, "valid-token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void processMove_bonusActionRemains_buildsDtoWithoutAdvancing() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        Pawn current = pawn(1L, 16, 8);
        Pawn opponent = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(false);
        // requireTurn → if(hasBonusAction) → after consume → all true: more bonus remains
        when(gameStateCache.hasBonusAction(10L, 1L)).thenReturn(true, true, true);
        when(gameService.buildGameGetDTO(testGame, 1L)).thenReturn(gameGetDTO);

        moveService.processMove(10L, dto, "valid-token");

        verify(gameStateCache).consumeBonusAction(10L, 1L);
        verify(gameService, never()).advanceTurn(any());
        verify(gameService).buildGameGetDTO(testGame, 1L);
    }

    @Test
    public void processMove_lastBonusConsumed_advancesTurn() {
        MovePostDTO dto = new MovePostDTO();
        dto.setTargetField(new int[] {14, 8});

        Pawn current = pawn(1L, 16, 8);
        Pawn opponent = pawn(2L, 0, 8);

        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(current, opponent));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(current);
        when(gameService.checkWinCondition(testGame, 1L)).thenReturn(false);
        // requireTurn → if(hasBonusAction) true, then after consume false → advance
        when(gameStateCache.hasBonusAction(10L, 1L)).thenReturn(true, true, false);

        moveService.processMove(10L, dto, "valid-token");

        verify(gameStateCache).consumeBonusAction(10L, 1L);
        verify(gameService).advanceTurn(testGame);
    }

    // ════════════════════════════════════════════════
    // Additional applyWallPlacement tests
    // ════════════════════════════════════════════════
    @Test
    public void applyWallPlacement_invalidToken_throwsUnauthorized() {
        when(userRepository.findByToken("bad")).thenReturn(null);
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "bad"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    public void applyWallPlacement_notUsersTurn_throwsForbidden() {
        testGame.setCurrentTurnUserId(2L);
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void applyWallPlacement_inactivePlayer_throwsForbidden() {
        testGame.setActivePlayerIds(Arrays.asList(2L));
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    public void applyWallPlacement_invalidTargetField_throwsBadRequest() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> moveService.applyWallPlacement(10L, dto, "valid-token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    public void applyWallPlacement_vertical_placesWallAndAdvancesTurn() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.VERTICAL);

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8), pawn(2L, 0, 8)));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);

        GameGetDTO result = moveService.applyWallPlacement(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).placeWall(10L, 7, 7, WallOrientation.VERTICAL, 1L);
    }

    @Test
    public void applyWallPlacement_bonusActionRemains_doesNotAdvanceTurn() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8), pawn(2L, 0, 8)));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        // requireTurn → if(hasBonusAction) → after consume → all true: more bonus remains
        when(gameStateCache.hasBonusAction(10L, 1L)).thenReturn(true, true, true);
        when(gameService.buildGameGetDTO(testGame, 1L)).thenReturn(gameGetDTO);

        moveService.applyWallPlacement(10L, dto, "valid-token");

        verify(gameStateCache).consumeBonusAction(10L, 1L);
        verify(gameService, never()).advanceTurn(any());
    }

    @Test
    public void applyWallPlacement_extraWallsBudgetAllowsMore() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8), pawn(2L, 0, 8)));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        // wallsPerPlayer=10, extras=2 → total=12. Consumed=11 → 1 remaining.
        when(gameStateCache.getPermanentlyConsumedWalls(10L, 1L)).thenReturn(11);
        when(gameStateCache.getExtraWalls(10L, 1L)).thenReturn(2);

        GameGetDTO result = moveService.applyWallPlacement(10L, dto, "valid-token");

        assertEquals(gameGetDTO, result);
        verify(gameStateCache).placeWall(10L, 7, 7, WallOrientation.HORIZONTAL, 1L);
    }

    @Test
    public void applyWallPlacement_clearsFreezeAfterWallPlacement() {
        WallPostDTO dto = new WallPostDTO();
        dto.setTargetField(new int[] {7, 7});
        dto.setOrientation(WallOrientation.HORIZONTAL);

        when(gameStateCache.getWalls(10L)).thenReturn(new ArrayList<>());
        when(gameStateCache.getPawns(10L)).thenReturn(Arrays.asList(
                pawn(1L, 16, 8), pawn(2L, 0, 8)));
        when(gameStateCache.getWallGrid(10L)).thenReturn(new boolean[17][17]);
        when(gameStateCache.isFrozen(10L, 1L)).thenReturn(true);

        moveService.applyWallPlacement(10L, dto, "valid-token");

        verify(gameStateCache).clearFreeze(10L, 1L);
    }

    private Pawn pawn(Long userId, int row, int col) {
        Pawn pawn = new Pawn();
        pawn.setUserId(userId);
        pawn.setRow(row);
        pawn.setCol(col);
        return pawn;
    }
}