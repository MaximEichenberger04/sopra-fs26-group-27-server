package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessagePostDTO;
import ch.uzh.ifi.hase.soprafs26.service.ChatService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GameWebSocketHandler webSocketHandler;

    // ---------- POST /games/{gameId}/chat ----------
    @Test
    public void sendMessage_validBody_returnsCreatedAndBroadcasts() throws Exception {
        ChatMessagePostDTO post = new ChatMessagePostDTO();
        post.setUserId(1L);
        post.setUsername("alice");
        post.setText("hello");

        ChatMessageGetDTO returned = new ChatMessageGetDTO();
        returned.setId(42L);
        returned.setGameId(5L);
        returned.setUserId(1L);
        returned.setUsername("alice");
        returned.setText("hello");
        returned.setTimestamp(1234L);

        given(chatService.sendMessage(eq(5L), any(ChatMessagePostDTO.class))).willReturn(returned);

        mockMvc.perform(post("/games/5/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "valid-token")
                        .content(asJsonString(post)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.gameId", is(5)))
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.text", is("hello")));

        verify(userService).validateToken("valid-token");
        verify(webSocketHandler).broadcastGameEvent("CHAT", 5L);
    }

    @Test
    public void sendMessage_withGifUrl_passesGifThrough() throws Exception {
        ChatMessagePostDTO post = new ChatMessagePostDTO();
        post.setUserId(1L);
        post.setUsername("alice");
        post.setGifUrl("https://example.com/cat.gif");

        ChatMessageGetDTO returned = new ChatMessageGetDTO();
        returned.setId(7L);
        returned.setGifUrl("https://example.com/cat.gif");

        given(chatService.sendMessage(eq(5L), any(ChatMessagePostDTO.class))).willReturn(returned);

        mockMvc.perform(post("/games/5/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "valid-token")
                        .content(asJsonString(post)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gifUrl", is("https://example.com/cat.gif")));
    }

    @Test
    public void sendMessage_invalidToken_returnsUnauthorized() throws Exception {
        ChatMessagePostDTO post = new ChatMessagePostDTO();
        post.setUserId(1L);
        post.setText("hi");

        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(userService).validateToken("bad-token");

        mockMvc.perform(post("/games/5/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "bad-token")
                        .content(asJsonString(post)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /games/{gameId}/chat ----------
    @Test
    public void getChatHistory_returnsList() throws Exception {
        ChatMessageGetDTO a = new ChatMessageGetDTO();
        a.setId(1L);
        a.setUsername("alice");
        a.setText("hi");
        ChatMessageGetDTO b = new ChatMessageGetDTO();
        b.setId(2L);
        b.setUsername("bob");
        b.setText("hey");
        given(chatService.getChatHistory(5L)).willReturn(List.of(a, b));

        mockMvc.perform(get("/games/5/chat")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username", is("alice")))
                .andExpect(jsonPath("$[1].username", is("bob")));

        verify(userService).validateToken("valid-token");
    }

    @Test
    public void getChatHistory_empty_returnsEmptyArray() throws Exception {
        given(chatService.getChatHistory(5L)).willReturn(Collections.emptyList());

        mockMvc.perform(get("/games/5/chat")
                        .header("Authorization", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getChatHistory_invalidToken_returnsUnauthorized() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .when(userService).validateToken("bad");

        mockMvc.perform(get("/games/5/chat")
                        .header("Authorization", "bad"))
                .andExpect(status().isUnauthorized());
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.toString());
        }
    }
}
