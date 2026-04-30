package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.Wall;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameStateCache gameStateCache;

    @Mock
    private ChatCache chatCache;

    @InjectMocks
    private GameService gameService;

    private Game game;
    private User user;
    private Lobby lobby;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setToken("valid-token");

        lobby = new Lobby();
        lobby.setHostId(1L);
        lobby.setPlayerIds(new ArrayList<>(Arrays.asList(1L, 2L)));
        lobby.setMaxPlayers(2);
        lobby.setMapTheme("medieval");

        game = new Game();
        game.setId(10L);
        game.setLobbyId(5L);
        game.setGameStatus(GameStatus.RUNNING);
        game.setPlayerIds(new ArrayList<>(Arrays.asList(1L, 2L)));
        game.setActivePlayerIds(new ArrayList<>(Arrays.asList(1L, 2L)));
        game.setCurrentTurnUserId(1L);
        game.setWallsPerPlayer(10);
        game.setSizeBoard(9);
        game.setMapTheme("medieval");
    }


    @Test
    void createGameFromLobby_validInput_createsGame() {
        when(lobbyRepository.findById(5L)).thenReturn(Optional.of(lobby));
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> {
            Game g = inv.getArgument(0);
            g.setId(10L);
            return g;
        });
        doNothing().when(gameStateCache).initGame(anyLong(), any());
        doNothing().when(chatCache).initGame(anyLong());

        Game created = gameService.createGameFromLobby(5L, "valid-token");

        assertNotNull(created);
        assertEquals(GameStatus.RUNNING, created.getGameStatus());
        assertEquals(10, created.getWallsPerPlayer()); // 2 players → 10 walls
        assertEquals("medieval", created.getMapTheme());
        assertEquals(1L, created.getCurrentTurnUserId());
        verify(gameStateCache).initGame(anyLong(), eq(lobby.getPlayerIds()));
        verify(chatCache).initGame(anyLong());
        verify(lobbyRepository).save(lobby);
    }

    @Test
    void createGameFromLobby_lobbyNotFound_throwsNotFound() {
        when(lobbyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> gameService.createGameFromLobby(99L, "valid-token"));
    }

    @Test
    void createGameFromLobby_invalidToken_throwsUnauthorized() {
        when(lobbyRepository.findById(5L)).thenReturn(Optional.of(lobby));
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> gameService.createGameFromLobby(5L, "bad-token"));
    }

    @Test
    void createGameFromLobby_fourPlayers_setsFiveWallsPerPlayer() {
        lobby.setMaxPlayers(4);
        lobby.setPlayerIds(new ArrayList<>(Arrays.asList(1L, 2L, 3L, 4L)));
        when(lobbyRepository.findById(5L)).thenReturn(Optional.of(lobby));
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        Game created = gameService.createGameFromLobby(5L, "valid-token");

        assertEquals(5, created.getWallsPerPlayer());
    }


    @Test
    void getGameById_existingGame_returnsDTO() {
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO dto = gameService.getGameById(10L);

        assertNotNull(dto);
        assertEquals(10L, dto.getId());
    }

    @Test
    void getGameById_nonExistingGame_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> gameService.getGameById(99L));
    }


    @Test
    void buildGameGetDTO_correctRemainingWalls() {
        Wall wall = new Wall();
        wall.setUserId(1L);
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of(wall)); // player 1 used 1 wall

        GameGetDTO dto = gameService.buildGameGetDTO(game);

        assertEquals(9, dto.getRemainingWalls().get(1L)); // 10 - 1 = 9
        assertEquals(10, dto.getRemainingWalls().get(2L)); // 10 - 0 = 10
    }

    @Test
    void buildGameGetDTO_noWallsUsed_allPlayersHaveFullBudget() {
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO dto = gameService.buildGameGetDTO(game);

        assertEquals(10, dto.getRemainingWalls().get(1L));
        assertEquals(10, dto.getRemainingWalls().get(2L));
    }


    @Test
    void checkWinCondition_playerZeroAtGoalRow_returnsTrue() {
        Pawn pawn = new Pawn();
        pawn.setRow(0);
        pawn.setCol(8);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(pawn);

        assertTrue(gameService.checkWinCondition(game, 1L)); // index 0 → goal row 0
    }

    @Test
    void checkWinCondition_playerZeroNotAtGoal_returnsFalse() {
        Pawn pawn = new Pawn();
        pawn.setRow(14);
        pawn.setCol(8);
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(pawn);

        assertFalse(gameService.checkWinCondition(game, 1L));
    }

    @Test
    void checkWinCondition_playerOneAtGoalRow_returnsTrue() {
        Pawn pawn = new Pawn();
        pawn.setRow(16);
        pawn.setCol(8);
        when(gameStateCache.getPawn(10L, 2L)).thenReturn(pawn);

        assertTrue(gameService.checkWinCondition(game, 2L)); // index 1 → goal row 16
    }

    @Test
    void checkWinCondition_unknownPlayer_returnsFalse() {
        assertFalse(gameService.checkWinCondition(game, 999L));
    }

    @Test
    void checkWinCondition_pawnIsNull_returnsFalse() {
        when(gameStateCache.getPawn(10L, 1L)).thenReturn(null);

        assertFalse(gameService.checkWinCondition(game, 1L));
    }


    @Test
    void forfeitGame_validRequest_endsGame() {
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.forfeitGame(10L, "valid-token");

        assertNotNull(result);
        // player 1 forfeited → player 2 (id=2) should be the winner
        assertEquals(2L, result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
        verify(gameStateCache).removePawn(10L, 1L);
    }

    @Test
    void forfeitGame_gameAlreadyEnded_returnsCurrentState() {
        game.setGameStatus(GameStatus.ENDED);
        game.setWinnerId(2L);
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findByToken("valid-token")).thenReturn(user);
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.forfeitGame(10L, "valid-token");

        assertEquals(GameStatus.ENDED, result.getGameStatus());
        verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void forfeitGame_invalidToken_throwsUnauthorized() {
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> gameService.forfeitGame(10L, "bad-token"));
    }

    @Test
    void forfeitGame_gameNotFound_throwsNotFound() {
        when(gameRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> gameService.forfeitGame(99L, "valid-token"));
    }


    @Test
    void forfeitDisconnectedPlayer_disconnectedPlayerIsRemoved_otherPlayerWins() {
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.forfeitDisconnectedPlayer(10L, 1L);

        assertEquals(2L, result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
        verify(gameStateCache).removePawn(10L, 1L);
    }

    @Test
    void forfeitDisconnectedPlayer_gameAlreadyEnded_returnsCurrentStateWithoutChanges() {
        game.setGameStatus(GameStatus.ENDED);
        game.setWinnerId(2L);
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.forfeitDisconnectedPlayer(10L, 1L);

        assertEquals(GameStatus.ENDED, result.getGameStatus());
        verify(gameRepository, never()).saveAndFlush(any());
    }

    @Test
    void forfeitDisconnectedPlayer_playerNotActive_returnsCurrentState() {
        game.setActivePlayerIds(new ArrayList<>(List.of(2L))); // player 1 already gone
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.forfeitDisconnectedPlayer(10L, 1L);

        assertNull(result.getWinnerId()); // no change triggered
    }


    @Test
    void advanceTurn_normalRoundRobin_advancesToNextPlayer() {
        game.setCurrentTurnUserId(1L);

        gameService.advanceTurn(game);

        assertEquals(2L, game.getCurrentTurnUserId());
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    void advanceTurn_lastPlayerInList_wrapsAroundToFirst() {
        game.setCurrentTurnUserId(2L);

        gameService.advanceTurn(game);

        assertEquals(1L, game.getCurrentTurnUserId());
    }

    @Test
    void advanceTurn_noActivePlayers_throwsBadRequest() {
        game.setActivePlayerIds(new ArrayList<>());

        assertThrows(ResponseStatusException.class,
                () -> gameService.advanceTurn(game));
    }


    @Test
    void endGame_setsWinnerAndStatusEnded() {
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        GameGetDTO result = gameService.endGame(game, 2L);

        assertEquals(2L, result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
        verify(gameStateCache).evictGame(10L);
        verify(chatCache).evictGame(10L);
    }

    @Test
    void endGame_evictsCacheForBothGameStateAndChat() {
        when(gameStateCache.getPawns(10L)).thenReturn(List.of());
        when(gameStateCache.getWalls(10L)).thenReturn(List.of());

        gameService.endGame(game, 1L);

        verify(gameStateCache, times(1)).evictGame(10L);
        verify(chatCache, times(1)).evictGame(10L);
    }
}