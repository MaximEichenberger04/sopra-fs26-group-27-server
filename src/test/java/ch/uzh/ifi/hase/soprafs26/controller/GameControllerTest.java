package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MatchHistoryGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.AbilityService;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.MoveService;
import ch.uzh.ifi.hase.soprafs26.service.StatisticsService;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GameService gameService;

    @Mock
    private MoveService moveService;

    @Mock
    private AbilityService abilityService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameWebSocketHandler webSocketHandler;

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private GameController gameController;

    private GameGetDTO runningGameDTO;
    private GameGetDTO endedGameDTO;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gameController).build();

        // Mock user for token resolution
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setToken("valid-token");
        lenient().when(userRepository.findByToken("valid-token")).thenReturn(mockUser);
        lenient().when(userRepository.findByToken("bad-token")).thenReturn(null);
        lenient().when(userRepository.findByToken("guest-token")).thenReturn(null);

        runningGameDTO = new GameGetDTO();
        runningGameDTO.setId(10L);
        runningGameDTO.setGameStatus(GameStatus.RUNNING);
        runningGameDTO.setCurrentTurnUserId(1L);
        runningGameDTO.setPlayerIds(List.of(1L, 2L));
        runningGameDTO.setWallsPerPlayer(10);
        runningGameDTO.setMapTheme("medieval");
        runningGameDTO.setPawns(List.of());
        runningGameDTO.setWalls(List.of());
        runningGameDTO.setRemainingWalls(Map.of(1L, 10, 2L, 10));

        endedGameDTO = new GameGetDTO();
        endedGameDTO.setId(10L);
        endedGameDTO.setGameStatus(GameStatus.ENDED);
        endedGameDTO.setWinnerId(2L);
        endedGameDTO.setPlayerIds(List.of(1L, 2L));
        endedGameDTO.setPawns(List.of());
        endedGameDTO.setWalls(List.of());
        endedGameDTO.setRemainingWalls(Map.of(1L, 10, 2L, 10));

    }


    @Test
    void getGame_validId_returns200WithDTO() throws Exception {
        when(gameService.getGameById(eq(10L), any())).thenReturn(runningGameDTO);

        mockMvc.perform(get("/games/10")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.gameStatus").value("RUNNING"))
                .andExpect(jsonPath("$.mapTheme").value("medieval"));
    }

    @Test
    void getGame_nonExistentId_returns404() throws Exception {
        when(gameService.getGameById(eq(999L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        mockMvc.perform(get("/games/999")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isNotFound());
    }


    @Test
    void submitMove_validMove_returns200AndBroadcastsMOVE() throws Exception {
        when(moveService.processMove(eq(10L), any(), eq("valid-token")))
                .thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/move")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[14,8]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameStatus").value("RUNNING"));

        verify(webSocketHandler).broadcastGameEvent("MOVE", 10L);
        verify(webSocketHandler, never()).broadcastGameEvent(eq("GAME_OVER"), anyLong());
    }

    @Test
    void submitMove_winningMove_returns200AndBroadcastsGAME_OVER() throws Exception {
        when(moveService.processMove(eq(10L), any(), eq("valid-token")))
                .thenReturn(endedGameDTO);

        mockMvc.perform(post("/games/10/move")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[0,8]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winnerId").value(2))
                .andExpect(jsonPath("$.gameStatus").value("ENDED"));

        verify(webSocketHandler).broadcastGameEvent("GAME_OVER", 10L);
        verify(webSocketHandler, never()).broadcastGameEvent(eq("MOVE"), anyLong());
    }

    @Test
    void submitMove_invalidMove_returns409() throws Exception {
        when(moveService.processMove(eq(10L), any(), eq("valid-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Invalid move"));

        mockMvc.perform(post("/games/10/move")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[14,8]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void submitMove_notPlayersTurn_returns403() throws Exception {
        when(moveService.processMove(eq(10L), any(), eq("guest-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your turn"));

        mockMvc.perform(post("/games/10/move")
                        .header("Authorization", "guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[14,8]}"))
                .andExpect(status().isForbidden());
    }


    @Test
    void submitWall_validPlacement_returns200AndBroadcastsWALL() throws Exception {
        when(moveService.applyWallPlacement(eq(10L), any(), eq("valid-token")))
                .thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/wall")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[8,7],\"orientation\":\"HORIZONTAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameStatus").value("RUNNING"));

        verify(webSocketHandler).broadcastGameEvent("WALL", 10L);
    }

    @Test
    void submitWall_noWallsLeft_returns422() throws Exception {
        when(moveService.applyWallPlacement(eq(10L), any(), eq("valid-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No walls remaining"));

        mockMvc.perform(post("/games/10/wall")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[8,7],\"orientation\":\"HORIZONTAL\"}"))
                .andExpect(status().isUnprocessableEntity());

        verify(webSocketHandler, never()).broadcastGameEvent(any(), anyLong());
    }

    @Test
    void submitWall_wallBlocksAllPaths_returns409() throws Exception {
        when(moveService.applyWallPlacement(eq(10L), any(), eq("valid-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Wall blocks all paths"));

        mockMvc.perform(post("/games/10/wall")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetField\":[8,7],\"orientation\":\"VERTICAL\"}"))
                .andExpect(status().isConflict());
    }


    @Test
    void forfeitGame_validRequest_returns200AndBroadcastsFORFEITAndGAME_OVER() throws Exception {
        when(gameService.forfeitGame(10L, "valid-token")).thenReturn(endedGameDTO);

        mockMvc.perform(post("/games/10/forfeit")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winnerId").value(2))
                .andExpect(jsonPath("$.gameStatus").value("ENDED"));

        verify(webSocketHandler).broadcastGameEvent("PLAYER_FORFEITED", 10L, 1L, null);
        verify(webSocketHandler).broadcastGameEvent("GAME_OVER", 10L);
    }

    @Test
    void forfeitGame_gameNotFound_returns404() throws Exception {
        when(gameService.forfeitGame(eq(999L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        mockMvc.perform(post("/games/999/forfeit")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void forfeitGame_invalidToken_returns401() throws Exception {
        when(gameService.forfeitGame(eq(10L), eq("bad-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));

        mockMvc.perform(post("/games/10/forfeit")
                        .header("Authorization", "bad-token"))
                .andExpect(status().isUnauthorized());

        verify(webSocketHandler, never()).broadcastGameEvent(any(), anyLong());
    }

    @Test
    void forfeitGame_alreadyEndedGame_stillReturns200() throws Exception {
        when(gameService.forfeitGame(10L, "valid-token")).thenReturn(endedGameDTO);

        mockMvc.perform(post("/games/10/forfeit")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameStatus").value("ENDED"));
    }

    @Test
    void forfeitGame_runningGameAfterForfeit_broadcastsGAME_UPDATED() throws Exception {
        // No winner yet (4-player game where 1 forfeits but 3 remain active)
        runningGameDTO.setWinnerId(null);
        when(gameService.forfeitGame(10L, "valid-token")).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/forfeit")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("PLAYER_FORFEITED", 10L, 1L, null);
        verify(webSocketHandler).broadcastGameEvent("GAME_UPDATED", 10L);
        verify(webSocketHandler, never()).broadcastGameEvent(eq("GAME_OVER"), anyLong());
    }

    @Test
    void forfeitGame_userMissing_broadcastsGenericFORFEIT() throws Exception {
        runningGameDTO.setWinnerId(null);
        when(gameService.forfeitGame(10L, "guest-token")).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/forfeit")
                        .header("Authorization", "guest-token"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("FORFEIT", 10L);
    }

    // ════════════════════════════════════════════════
    // POST /games/{gameId}/skip
    // ════════════════════════════════════════════════
    @Test
    void skipTurn_validRequest_returns200AndBroadcastsSKIP() throws Exception {
        when(gameService.skipTurn(10L, "valid-token")).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/skip")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("SKIP", 10L);
    }

    @Test
    void skipTurn_notMyTurn_returns403() throws Exception {
        when(gameService.skipTurn(10L, "guest-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your turn"));

        mockMvc.perform(post("/games/10/skip")
                        .header("Authorization", "guest-token"))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════
    // POST /games/{gameId}/ability
    // ════════════════════════════════════════════════
    @Test
    void useAbility_fireball_broadcastsFIREBALLWithCoords() throws Exception {
        when(abilityService.useAbility(eq(10L), any(), eq("valid-token"))).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/ability")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abilityType\":\"FIREBALL\",\"targetRow\":3,\"targetCol\":4}"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("FIREBALL", 10L, 3, 4);
    }

    @Test
    void useAbility_earthquake_broadcastsEARTHQUAKEWithCoords() throws Exception {
        when(abilityService.useAbility(eq(10L), any(), eq("valid-token"))).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/ability")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abilityType\":\"EARTHQUAKE\",\"targetRow\":5,\"targetCol\":6}"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("EARTHQUAKE", 10L, 5, 6);
    }

    @Test
    void useAbility_freeze_broadcastsGenericABILITY_USED() throws Exception {
        when(abilityService.useAbility(eq(10L), any(), eq("valid-token"))).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/ability")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abilityType\":\"FREEZE\",\"targetUserId\":2}"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("ABILITY_USED", 10L);
    }

    @Test
    void useAbility_twoMoves_broadcastsGenericABILITY_USED() throws Exception {
        when(abilityService.useAbility(eq(10L), any(), eq("valid-token"))).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/ability")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abilityType\":\"TWO_MOVES\"}"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("ABILITY_USED", 10L);
    }

    @Test
    void useAbility_serviceForbids_returns403() throws Exception {
        when(abilityService.useAbility(eq(10L), any(), eq("valid-token")))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your turn"));

        mockMvc.perform(post("/games/10/ability")
                        .header("Authorization", "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abilityType\":\"TWO_MOVES\"}"))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════
    // POST /games/{gameId}/ability/draw
    // ════════════════════════════════════════════════
    @Test
    void drawAbilityCard_valid_returns200AndBroadcastsABILITY_DRAW() throws Exception {
        when(abilityService.drawCard(10L, "valid-token")).thenReturn(runningGameDTO);

        mockMvc.perform(post("/games/10/ability/draw")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk());

        verify(webSocketHandler).broadcastGameEvent("ABILITY_DRAW", 10L);
    }

    @Test
    void drawAbilityCard_notChaosMode_returns400() throws Exception {
        when(abilityService.drawCard(10L, "valid-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not chaos"));

        mockMvc.perform(post("/games/10/ability/draw")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════
    // GET /games/{gameId}/results
    // ════════════════════════════════════════════════
    @Test
    void getGameResults_validRequest_returnsList() throws Exception {
        MatchHistoryGetDTO a = new MatchHistoryGetDTO();
        a.setId(1L);
        a.setUserId(1L);
        a.setGameId(10L);
        a.setWon(true);
        MatchHistoryGetDTO b = new MatchHistoryGetDTO();
        b.setId(2L);
        b.setUserId(2L);
        b.setGameId(10L);
        b.setWon(false);
        when(statisticsService.getGameResults(10L, "valid-token")).thenReturn(List.of(a, b));

        mockMvc.perform(get("/games/10/results")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].won").value(true))
                .andExpect(jsonPath("$[1].won").value(false));
    }

    @Test
    void getGameResults_invalidToken_returns401() throws Exception {
        when(statisticsService.getGameResults(10L, "bad-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/games/10/results")
                        .header("Authorization", "bad-token"))
                .andExpect(status().isUnauthorized());
    }
}