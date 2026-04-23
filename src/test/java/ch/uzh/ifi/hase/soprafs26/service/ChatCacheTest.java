package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatCacheTest {

    private ChatCache cache;

    // helper
    private ChatMessage message(long id, long userId, String text) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setGameId(1L);
        m.setUserId(userId);
        m.setText(text);
        return m;
    }

    @BeforeEach
    public void setup() {
        cache = new ChatCache();
    }

    @Test
    public void initGame_historyIsEmpty() {
        cache.initGame(1L);
        assertTrue(cache.getGameHistory(1L).isEmpty());
    }

    @Test
    public void addGameMessage_appendsMessage() {
        cache.initGame(1L);
        ChatMessage msg = message(1L, 10L, "hello");

        cache.addGameMessage(1L, msg);

        List<ChatMessage> history = cache.getGameHistory(1L);
        assertEquals(1, history.size());
        assertEquals("hello", history.get(0).getText());
    }

    @Test
    public void addGameMessage_multipleMessages_preservesOrder() {
        cache.initGame(1L);
        cache.addGameMessage(1L, message(1L, 10L, "first"));
        cache.addGameMessage(1L, message(2L, 20L, "second"));
        cache.addGameMessage(1L, message(3L, 10L, "third"));

        List<ChatMessage> history = cache.getGameHistory(1L);
        assertEquals(3, history.size());
        assertEquals("first",  history.get(0).getText());
        assertEquals("second", history.get(1).getText());
        assertEquals("third",  history.get(2).getText());
    }

    @Test
    public void addGameMessage_unknownGame_throws404() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.addGameMessage(99L, message(1L, 10L, "hi")));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void getGameHistory_unknownGame_returnsEmptyList() {
        List<ChatMessage> result = cache.getGameHistory(99L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void evictGame_removesHistory() {
        cache.initGame(1L);
        cache.addGameMessage(1L, message(1L, 10L, "hi"));

        cache.evictGame(1L);

        assertTrue(cache.getGameHistory(1L).isEmpty());
    }

    @Test
    public void evictGame_afterEvict_addMessageThrows404() {
        cache.initGame(1L);
        cache.evictGame(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> cache.addGameMessage(1L, message(1L, 10L, "hi")));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    public void initGame_reinitAfterEvict_startsEmpty() {
        cache.initGame(1L);
        cache.addGameMessage(1L, message(1L, 10L, "old"));
        cache.evictGame(1L);
        cache.initGame(1L);

        assertTrue(cache.getGameHistory(1L).isEmpty());
    }

}
