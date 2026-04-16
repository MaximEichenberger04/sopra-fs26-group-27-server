package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Service
public class GameDisconnectService {

    private static final int GRACE_PERIOD_SECONDS = 30;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    private final GameRepository gameRepository;
    private final GameService gameService;
    private final GameWebSocketHandler gameWebSocketHandler;

    public GameDisconnectService(GameRepository gameRepository,
                                 GameService gameService,
                                 GameWebSocketHandler gameWebSocketHandler) {
        this.gameRepository = gameRepository;
        this.gameService = gameService;
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    public void handleDisconnect(Long gameId, Long userId) {
        String key = key(gameId, userId);

        ScheduledFuture<?> existing = pendingForfeits.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }

        gameWebSocketHandler.broadcastGameEvent("PLAYER_DISCONNECTED", gameId, userId, GRACE_PERIOD_SECONDS);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null || game.getGameStatus() == GameStatus.ENDED) {
                pendingForfeits.remove(key);
                return;
            }

            if (gameWebSocketHandler.isPlayerConnected(gameId, userId)) {
                pendingForfeits.remove(key);
                return;
            }

            gameService.forfeitDisconnectedPlayer(gameId, userId);
            gameWebSocketHandler.broadcastGameEvent("PLAYER_FORFEITED", gameId, userId, null);
            gameWebSocketHandler.broadcastGameEvent("GAME_UPDATED", gameId, null, null);

            pendingForfeits.remove(key);
        }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

        pendingForfeits.put(key, future);
    }

    public void handleReconnect(Long gameId, Long userId) {
        String key = key(gameId, userId);
        ScheduledFuture<?> future = pendingForfeits.remove(key);
        if (future != null) {
            future.cancel(false);
            gameWebSocketHandler.broadcastGameEvent("PLAYER_RECONNECTED", gameId, userId, null);
        }
    }

    private String key(Long gameId, Long userId) {
        return gameId + ":" + userId;
    }
}