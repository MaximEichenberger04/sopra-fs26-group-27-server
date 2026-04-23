package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.WallOrientation;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MovePostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.WallPostDTO;
import ch.uzh.ifi.hase.soprafs26.service.GameService;
import ch.uzh.ifi.hase.soprafs26.service.MoveService;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameController.class)
public class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private GameWebSocketHandler gameWebSocketHandler;

    @MockitoBean
    private MoveService moveService;

    @Test
    public void movePawn_validInput_returnsGameGetDTO() throws Exception {
        GameGetDTO returnedGame = new GameGetDTO();
        returnedGame.setId(1L);
        returnedGame.setGameStatus(GameStatus.RUNNING);

        MovePostDTO movePostDTO = new MovePostDTO();
        movePostDTO.setTargetField(new int[]{14, 8});

        when(moveService.processMove(eq(1L), any(MovePostDTO.class), eq("test-token")))
                .thenReturn(returnedGame);

        mockMvc.perform(post("/games/1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token")
                        .content(objectMapper.writeValueAsString(movePostDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.gameStatus").value("RUNNING"));

        verify(moveService).processMove(eq(1L), any(MovePostDTO.class), eq("test-token"));
    }

    @Test
    public void placeWall_validInput_returnsOk() throws Exception {
        WallPostDTO wallPostDTO = new WallPostDTO();
        wallPostDTO.setTargetField(new int[]{7, 7});
        wallPostDTO.setOrientation(WallOrientation.HORIZONTAL);

        GameGetDTO returnedGame = new GameGetDTO();
        returnedGame.setId(2L);
        returnedGame.setGameStatus(GameStatus.RUNNING);

        when(moveService.applyWallPlacement(eq(2L), any(WallPostDTO.class), eq("wall-token")))
                .thenReturn(returnedGame);

        mockMvc.perform(post("/games/2/wall")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "wall-token")
                        .content(objectMapper.writeValueAsString(wallPostDTO)))
                .andExpect(status().isOk());

        verify(moveService).applyWallPlacement(eq(2L), any(WallPostDTO.class), eq("wall-token"));
    }

    @Test
    public void forfeit_validInput_returnsGameGetDTO() throws Exception {
        GameGetDTO returnedGame = new GameGetDTO();
        returnedGame.setId(3L);
        returnedGame.setGameStatus(GameStatus.ENDED);
        returnedGame.setWinnerId(2L);

        when(gameService.forfeitGame(eq(3L), eq("forfeit-token")))
                .thenReturn(returnedGame);

        mockMvc.perform(post("/games/3/forfeit")
                        .header("Authorization", "forfeit-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.gameStatus").value("ENDED"))
                .andExpect(jsonPath("$.winnerId").value(2));

        verify(gameService).forfeitGame(eq(3L), eq("forfeit-token"));
    }
}