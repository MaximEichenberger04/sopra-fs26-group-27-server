package ch.uzh.ifi.hase.soprafs26.controller;
 
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.LobbyPutDTO;
import ch.uzh.ifi.hase.soprafs26.service.LobbyService;
 
// Spring Boot 3.x uses tools.jackson (not com.fasterxml.jackson)
import tools.jackson.databind.ObjectMapper;
 
import org.junit.jupiter.api.Test;
 
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 3.4+: @WebMvcTest moved to spring-boot-webmvc-test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
// Spring Boot 3.4+: @MockBean replaced by @MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
 
import org.springframework.test.web.servlet.MockMvc;
 
import org.springframework.web.server.ResponseStatusException;
 
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
 
import static org.hamcrest.Matchers.hasSize;
 
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
 
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
 
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
@WebMvcTest(LobbyController.class)
public class LobbyControllerTest {
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private ObjectMapper objectMapper;
 
    @MockitoBean
    private LobbyService lobbyService;

    @Test
    public void givenLobbies_whenGetAllLobbies_thenReturnJsonArray() throws Exception {
        // given
        Lobby lobby = new Lobby();
        lobby.setId(1L);
        lobby.setName("Lobby");
        lobby.setInviteCode("123456");
        
        // list of lobbies
        List<Lobby> lobbies = Collections.singletonList(lobby);

        given(lobbyService.getLobbies()).willReturn(lobbies);

        // when / then
        mockMvc.perform(get("/lobbies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Lobby"))
                .andExpect(jsonPath("$[0].inviteCode").value("123456"));
    }

    @Test
    public void givenLobbyId_whenGetLobby_thenReturnLobby() throws Exception {
        // given
        Lobby lobby = new Lobby();
        lobby.setId(1L);
        lobby.setName("Lobby");
        lobby.setInviteCode("123456");

        given(lobbyService.getLobbyById(1L)).willReturn(lobby);

        // when / then
        mockMvc.perform(get("/lobbies/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Lobby"))
                .andExpect(jsonPath("$.inviteCode").value("123456"));
    }

    @Test
    public void createLobby_validInput_lobbyCreated() throws Exception {
        // given
        LobbyPostDTO lobbyPostDTO = new LobbyPostDTO();
        lobbyPostDTO.setName("Lobby");

        Lobby createdLobby = new Lobby();
        createdLobby.setId(1L);
        createdLobby.setName("Lobby");
        createdLobby.setInviteCode("123456");

        given(lobbyService.createLobby(any(Lobby.class), eq("test-token"))).willReturn(createdLobby);

        // when / then
        mockMvc.perform(post("/lobbies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token")
                        .content(objectMapper.writeValueAsString(lobbyPostDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Lobby"))
                .andExpect(jsonPath("$.inviteCode").value("123456"));
    }

    @Test
    public void updateLobby_validInput_returnsNoContent() throws Exception {
        // given
        LobbyPutDTO lobbyPutDTO = new LobbyPutDTO();
        lobbyPutDTO.setName("UpdatedLobby");

        // when / then
        mockMvc.perform(put("/lobbies/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token")
                        .content(objectMapper.writeValueAsString(lobbyPutDTO)))
                .andExpect(status().isNoContent());

        then(lobbyService).should().updateLobby(eq(1L), any(Lobby.class), eq("test-token"));
    }

    @Test
    public void startLobby_validInput_returnsCreatedGame() throws Exception {
        // given
        GameGetDTO gameGetDTO = new GameGetDTO();
        gameGetDTO.setId(99L);

        given(lobbyService.startLobby(1L, "test-token")).willReturn(gameGetDTO);

        // when / then
        mockMvc.perform(post("/lobbies/1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isCreated()) // expect 201
                .andExpect(jsonPath("$.id").value(99));
    }

    @Test
    public void startLobby_notEnoughPlayers_returnsBadRequest() throws Exception {
        // given
        Lobby lobby = new Lobby();
        lobby.setId(1L);
        lobby.setCurrentPlayers(2);
        lobby.setMaxPlayers(4);

        given(lobbyService.startLobby(1L, "test-token"))
            .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough players"));

        // when / then
        mockMvc.perform(post("/lobbies/1/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void joinLobbyByInviteCode_validInput_returnsLobby() throws Exception {
        // given
        Lobby lobby = new Lobby();
        lobby.setId(2L);
        lobby.setName("InvitedLobby");
        lobby.setInviteCode("VALID");

        given(lobbyService.joinLobbyByInviteCode("VALID", "test-token")).willReturn(lobby);

        // when / then
        mockMvc.perform(post("/lobbies/join/VALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("InvitedLobby"))
                .andExpect(jsonPath("$.inviteCode").value("VALID"));
    }

    @Test
    public void joinLobbyById_validInput_returnsLobby() throws Exception {
        // given
        Lobby lobby = new Lobby();
        lobby.setId(3L);
        lobby.setName("OpenLobby");
        lobby.setInviteCode("VALID");

        given(lobbyService.joinLobbyById(3L, "test-token")).willReturn(lobby);

        // when / then
        mockMvc.perform(post("/lobbies/3/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("OpenLobby"))
                .andExpect(jsonPath("$.inviteCode").value("VALID"));
    }

    @Test
    public void leaveLobby_validInput_returnsNoContent() throws Exception {
        // when / then
        mockMvc.perform(post("/lobbies/1/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isNoContent());

        then(lobbyService).should().leaveLobby(1L, "test-token");
    }
    
    @Test
    public void leaveLobby_userNotInLobby_returnsBadRequest() throws Exception {
        // given
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are not in this lobby"))
            .when(lobbyService)
            .leaveLobby(1L, "test-token");

        // when / then
        mockMvc.perform(post("/lobbies/1/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "test-token"))
                .andExpect(status().isBadRequest());
    }
}