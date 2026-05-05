package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessagePostDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


public class ChatServiceTest {

    private static final String VALID_GIF = "https://static.klipy.com/some.gif";
    private static final String INVALID_GIF = "https://tenor.com/some.gif";

    @Mock
    private ChatCache chatCache;

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private ChatService chatService;

    private Game runningGame;

    // helper    
    private ChatMessage chatMessage(long id, long gameId, long userId, String text, String gifUrl) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setGameId(gameId);
        m.setUserId(userId);
        m.setText(text);
        m.setGifUrl(gifUrl);
        return m;
    }

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        runningGame = new Game();
        runningGame.setGameStatus(GameStatus.RUNNING);
        runningGame.setPlayerIds(List.of(10L, 20L));
    }

    // sendMessage: correct flow

    @Test
    public void sendMessage_textOnly_returnsDTO() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ChatMessageGetDTO result = chatService.sendMessage(1L, postDTO(10L, "hello", null));

        assertNotNull(result);
        assertEquals(10L, result.getUserId());
        assertEquals("hello", result.getText());
        assertNull(result.getGifUrl());
        assertEquals(1L, result.getId());
        verify(chatCache).addGameMessage(eq(1L), any(ChatMessage.class)); // asserts the message was persisted to the cache for game 1
    }

    @Test
    public void sendMessage_gifOnly_returnsDTO() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ChatMessageGetDTO result = chatService.sendMessage(1L, postDTO(10L, null, VALID_GIF));

        assertNull(result.getText());
        assertEquals(VALID_GIF, result.getGifUrl());
    }

    @Test
    public void sendMessage_setsGameIdAndUsername() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ChatMessagePostDTO dto = postDTO(10L, "hi", null);
        dto.setUsername("alice");

        ChatMessageGetDTO result = chatService.sendMessage(1L, dto);

        assertEquals(1L, result.getGameId());
        assertEquals("alice", result.getUsername());
    }

    @Test
    public void sendMessage_setsTimestamp() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));
        long before = System.currentTimeMillis();

        ChatMessageGetDTO result = chatService.sendMessage(1L, postDTO(10L, "hi", null));

        assertTrue(result.getTimestamp() >= before);
        assertTrue(result.getTimestamp() <= System.currentTimeMillis());
    }

    @Test
    public void sendMessage_idIncrements() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ChatMessageGetDTO first  = chatService.sendMessage(1L, postDTO(10L, "one", null));
        ChatMessageGetDTO second = chatService.sendMessage(1L, postDTO(20L, "two", null));

        assertEquals(1L, first.getId());
        assertEquals(2L, second.getId());
    }

    // sendMessage: game not found

    @Test
    public void sendMessage_gameNotFound_throws404() {
        when(gameRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, "hi", null)));
        assertEquals(404, ex.getStatusCode().value());
    }

    // sendMessage: game not running

    @Test
    public void sendMessage_gameNotRunning_throws409() {
        runningGame.setGameStatus(GameStatus.ENDED);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, "hi", null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    // sendMessage: player checks 

    @Test
    public void sendMessage_userIdNull_throws403() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ChatMessagePostDTO dto = postDTO(null, "hi", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, dto));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    public void sendMessage_userNotInGame_throws403() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(99L, "hi", null)));
        assertEquals(403, ex.getStatusCode().value());
    }

    // sendMessage: content validation 

    @Test
    public void sendMessage_noTextAndNoGif_throws400() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, null, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void sendMessage_blankTextAndNoGif_throws400() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, "   ", null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void sendMessage_textTooLong_throws400() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));
        String longText = "x".repeat(501);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, longText, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void sendMessage_textExactlyMaxLength_succeeds() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));
        String maxText = "x".repeat(500);

        assertDoesNotThrow(() -> chatService.sendMessage(1L, postDTO(10L, maxText, null)));
    }

    @Test
    public void sendMessage_invalidGifUrl_throws400() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, null, INVALID_GIF)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    public void sendMessage_blankGifUrl_andNoText_throws400() {
        when(gameRepository.findById(1L)).thenReturn(Optional.of(runningGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.sendMessage(1L, postDTO(10L, null, "  ")));
        assertEquals(400, ex.getStatusCode().value());
    }

    // getChatHistory

    @Test
    public void getChatHistory_gameNotFound_throws404() {
        when(gameRepository.existsById(1L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> chatService.getChatHistory(1L));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getChatHistory_returnsAllMessages() {
        when(gameRepository.existsById(1L)).thenReturn(true);
        when(chatCache.getGameHistory(1L)).thenReturn(List.of(
                chatMessage(1L, 1L, 10L, "hi",   null),
                chatMessage(2L, 1L, 20L, "hello", VALID_GIF)
        ));

        List<ChatMessageGetDTO> result = chatService.getChatHistory(1L);

        assertEquals(2, result.size());
        assertEquals("hi",    result.get(0).getText());
        assertEquals("hello", result.get(1).getText());
        assertEquals(VALID_GIF, result.get(1).getGifUrl());
    }

    @Test
    public void getChatHistory_emptyHistory_returnsEmptyList() {
        when(gameRepository.existsById(1L)).thenReturn(true);
        when(chatCache.getGameHistory(1L)).thenReturn(List.of());

        List<ChatMessageGetDTO> result = chatService.getChatHistory(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getChatHistory_dtosHaveCorrectFields() {
        when(gameRepository.existsById(1L)).thenReturn(true);
        ChatMessage msg = chatMessage(5L, 1L, 10L, "test", VALID_GIF);
        msg.setUsername("bob");
        msg.setTimestamp(12345L);
        when(chatCache.getGameHistory(1L)).thenReturn(List.of(msg));

        ChatMessageGetDTO dto = chatService.getChatHistory(1L).get(0);

        assertEquals(5L,        dto.getId());
        assertEquals(1L,        dto.getGameId());
        assertEquals(10L,       dto.getUserId());
        assertEquals("bob",     dto.getUsername());
        assertEquals("test",    dto.getText());
        assertEquals(VALID_GIF, dto.getGifUrl());
        assertEquals(12345L,    dto.getTimestamp());
    }

    // helpers

    private ChatMessagePostDTO postDTO(Long userId, String text, String gifUrl) {
        ChatMessagePostDTO dto = new ChatMessagePostDTO();
        dto.setUserId(userId);
        dto.setText(text);
        dto.setGifUrl(gifUrl);
        return dto;
    }

}
