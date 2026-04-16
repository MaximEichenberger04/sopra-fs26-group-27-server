package ch.uzh.ifi.hase.soprafs26.controller;

import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessageGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.ChatMessagePostDTO;
import ch.uzh.ifi.hase.soprafs26.service.ChatService;
import ch.uzh.ifi.hase.soprafs26.service.UserService;
import ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/games")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final GameWebSocketHandler webSocketHandler;

    ChatController(ChatService chatService,
                   UserService userService,
                   GameWebSocketHandler webSocketHandler) {
        this.chatService      = chatService;
        this.userService      = userService;
        this.webSocketHandler = webSocketHandler;
    }

    // POST /games/{gameId}/chat: send a message; broadcasts a CHAT event via WebSocket
    @PostMapping("/{gameId}/chat")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public ChatMessageGetDTO sendMessage(
            @PathVariable Long gameId,
            @RequestBody ChatMessagePostDTO dto,
            @RequestHeader("Authorization") String token) {

        userService.validateToken(token);
        ChatMessageGetDTO result = chatService.sendMessage(gameId, dto);
        webSocketHandler.broadcastGameEvent("CHAT", gameId);
        return result;
    }

    // GET  /games/{gameId}/chat: fetch the full chat history for a game
    @GetMapping("/{gameId}/chat")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<ChatMessageGetDTO> getChatHistory(
            @PathVariable Long gameId,
            @RequestHeader("Authorization") String token) {

        userService.validateToken(token);
        return chatService.getChatHistory(gameId);
    }
}
