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

    private Pawn pawn(Long userId, int row, int col) {
        Pawn pawn = new Pawn();
        pawn.setUserId(userId);
        pawn.setRow(row);
        pawn.setCol(col);
        return pawn;
    }
}