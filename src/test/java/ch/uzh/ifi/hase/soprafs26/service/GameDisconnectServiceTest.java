package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GameDisconnectServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameService gameService;

    @Mock
    private GameWebSocketHandler gameWebSocketHandler;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @InjectMocks
    private GameDisconnectService disconnectService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Replace the real scheduler with a mock to control scheduling
        ReflectionTestUtils.setField(disconnectService, "scheduler", scheduler);
        doReturn(scheduledFuture).when(scheduler)
                .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    public void handleDisconnect_broadcastsDisconnectedAndSchedulesForfeit() {
        disconnectService.handleDisconnect(7L, 42L);

        verify(gameWebSocketHandler).broadcastGameEvent("PLAYER_DISCONNECTED", 7L, 42L, 30);
        verify(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));

        @SuppressWarnings("unchecked")
        Map<String, ScheduledFuture<?>> pending =
                (Map<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(disconnectService, "pendingForfeits");
        assertNotNull(pending);
        assertTrue(pending.containsKey("7:42"));
    }

    @Test
    public void handleDisconnect_existingPendingTaskCancelled() {
        disconnectService.handleDisconnect(1L, 2L);
        // Disconnect again to trigger cancellation of the previous schedule
        disconnectService.handleDisconnect(1L, 2L);

        verify(scheduledFuture, atLeastOnce()).cancel(false);
    }

    @Test
    public void scheduledForfeit_gameMissing_doesNothing() {
        when(gameRepository.findById(1L)).thenReturn(Optional.empty());

        Runnable task = captureScheduledTask(1L, 2L);
        task.run();

        verify(gameService, never()).forfeitDisconnectedPlayer(anyLong(), anyLong());
        verify(gameWebSocketHandler, never()).broadcastGameEvent(eq("PLAYER_FORFEITED"), anyLong(), anyLong(), any());
    }

    @Test
    public void scheduledForfeit_gameAlreadyEnded_doesNothing() {
        Game ended = new Game();
        ReflectionTestUtils.setField(ended, "gameStatus", GameStatus.ENDED);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(ended));

        Runnable task = captureScheduledTask(1L, 2L);
        task.run();

        verify(gameService, never()).forfeitDisconnectedPlayer(anyLong(), anyLong());
    }

    @Test
    public void scheduledForfeit_playerReconnected_doesNotForfeit() {
        Game running = new Game();
        ReflectionTestUtils.setField(running, "gameStatus", GameStatus.RUNNING);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(running));
        when(gameWebSocketHandler.isPlayerConnected(1L, 2L)).thenReturn(true);

        Runnable task = captureScheduledTask(1L, 2L);
        task.run();

        verify(gameService, never()).forfeitDisconnectedPlayer(anyLong(), anyLong());
    }

    @Test
    public void scheduledForfeit_playerStillDisconnected_forfeitsAndBroadcasts() {
        Game running = new Game();
        ReflectionTestUtils.setField(running, "gameStatus", GameStatus.RUNNING);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(running));
        when(gameWebSocketHandler.isPlayerConnected(1L, 2L)).thenReturn(false);

        Runnable task = captureScheduledTask(1L, 2L);
        task.run();

        verify(gameService).forfeitDisconnectedPlayer(1L, 2L);
        verify(gameWebSocketHandler).broadcastGameEvent("PLAYER_FORFEITED", 1L, 2L, null);
        verify(gameWebSocketHandler).broadcastGameEvent("GAME_UPDATED", 1L, null, null);

        @SuppressWarnings("unchecked")
        Map<String, ScheduledFuture<?>> pending =
                (Map<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(disconnectService, "pendingForfeits");
        assertFalse(pending.containsKey("1:2"));
    }

    @Test
    public void handleReconnect_existingPendingTask_cancelsAndBroadcasts() {
        disconnectService.handleDisconnect(1L, 2L);

        disconnectService.handleReconnect(1L, 2L);

        verify(scheduledFuture).cancel(false);
        verify(gameWebSocketHandler).broadcastGameEvent("PLAYER_RECONNECTED", 1L, 2L, null);
    }

    @Test
    public void handleReconnect_noPendingTask_doesNothing() {
        disconnectService.handleReconnect(1L, 2L);

        verify(gameWebSocketHandler, never()).broadcastGameEvent(eq("PLAYER_RECONNECTED"), anyLong(), anyLong(), any());
    }

    // ---------- Helpers ----------
    private Runnable captureScheduledTask(long gameId, long userId) {
        disconnectService.handleDisconnect(gameId, userId);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).schedule(captor.capture(), anyLong(), any(TimeUnit.class));
        return captor.getValue();
    }
}
