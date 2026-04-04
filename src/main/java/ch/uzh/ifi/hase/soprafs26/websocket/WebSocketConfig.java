package ch.uzh.ifi.hase.soprafs26.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           ChatWebSocketHandler chatWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /**
     * Registers WebSocket handlers:
     *   /game_refresh_websocket, game state refresh (all sessions, flat list)
     *   /chat_refresh_websocket, in-game chat (sessions grouped by gameId query param)
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/game_refresh_websocket").setAllowedOrigins("*");
        registry.addHandler(chatWebSocketHandler, "/chat_refresh_websocket").setAllowedOrigins("*");
    }
}
