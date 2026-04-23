package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full Spring context integration tests for GameService.
 *
 * Uses an in-memory H2 database (configured in src/test/resources/application.properties).
 * Each test runs in its own transaction that is rolled back, except where
 * @DirtiesContext is needed to reset the cache state.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GameServiceIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private UserRepository userRepository;

    private User hostUser;
    private User guestUser;
    private Lobby lobby;

    @BeforeEach
    void setUp() {
        hostUser = new User();
        hostUser.setUsername("host");
        hostUser.setToken("host-token");
        hostUser.setPassword("hashed-pw");
        hostUser.setDisplayName("Host Player");
        hostUser.setStatus(ch.uzh.ifi.hase.soprafs26.constant.UserStatus.ONLINE);
        hostUser.setCreationDate(java.time.LocalDate.now());
        hostUser = userRepository.save(hostUser);

        guestUser = new User();
        guestUser.setUsername("guest");
        guestUser.setToken("guest-token");
        guestUser.setPassword("hashed-pw");
        guestUser.setDisplayName("Guest Player");
        guestUser.setStatus(ch.uzh.ifi.hase.soprafs26.constant.UserStatus.ONLINE);
        guestUser.setCreationDate(java.time.LocalDate.now());
        guestUser = userRepository.save(guestUser);

        lobby = new Lobby();
        lobby.setName("Test Lobby");
        lobby.setLobbyStatus(ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus.WAITING);
        lobby.setHostId(hostUser.getId());
        lobby.setMaxPlayers(2);
        lobby.setCurrentPlayers(2);
        lobby.setGameMode("STANDARD");
        lobby.setInviteCode(java.util.UUID.randomUUID().toString());
        lobby.setMapTheme("medieval");
        lobby.setPlayerIds(new ArrayList<>(Arrays.asList(hostUser.getId(), guestUser.getId())));
        lobby = lobbyRepository.save(lobby);
    }


    @Test
    void createGameFromLobby_persistsGameAndSetsLobbyGameId() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        assertNotNull(game.getId());
        assertEquals(GameStatus.RUNNING, game.getGameStatus());
        assertTrue(gameRepository.findById(game.getId()).isPresent());

        Lobby updatedLobby = lobbyRepository.findById(lobby.getId()).orElseThrow();
        assertEquals(game.getId(), updatedLobby.getGameId());
    }

    @Test
    void createGameFromLobby_twoPlayers_setsCorrectWallBudget() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        assertEquals(10, game.getWallsPerPlayer());
    }

    @Test
    void createGameFromLobby_mapsThemeFromLobbyToGame() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        assertEquals("medieval", game.getMapTheme());
    }

    @Test
    void createGameFromLobby_firstPlayerInLobbyStartsTurn() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        assertEquals(hostUser.getId(), game.getCurrentTurnUserId());
    }

    @Test
    void createGameFromLobby_invalidLobbyId_throwsNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameService.createGameFromLobby(999L, "host-token"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createGameFromLobby_invalidToken_throwsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameService.createGameFromLobby(lobby.getId(), "wrong-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }


    @Test
    void getGameById_returnsCorrectDTO() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        GameGetDTO dto = gameService.getGameById(game.getId());

        assertNotNull(dto);
        assertEquals(game.getId(), dto.getId());
        assertEquals(GameStatus.RUNNING, dto.getGameStatus());
        assertNotNull(dto.getPawns());
        assertNotNull(dto.getWalls());
        assertNotNull(dto.getRemainingWalls());
    }

    @Test
    void getGameById_nonExistentId_throwsNotFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameService.getGameById(999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }


    @Test
    void buildGameGetDTO_freshGame_allPlayersHaveFullWallBudget() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        GameGetDTO dto = gameService.getGameById(game.getId());

        assertEquals(10, dto.getRemainingWalls().get(hostUser.getId()));
        assertEquals(10, dto.getRemainingWalls().get(guestUser.getId()));
    }


    @Test
    void forfeitGame_hostForfeits_guestWins() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        GameGetDTO result = gameService.forfeitGame(game.getId(), "host-token");

        assertEquals(guestUser.getId(), result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
    }

    @Test
    void forfeitGame_guestForfeits_hostWins() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        GameGetDTO result = gameService.forfeitGame(game.getId(), "guest-token");

        assertEquals(hostUser.getId(), result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
    }

    @Test
    void forfeitGame_alreadyEnded_doesNotChangeWinner() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        gameService.forfeitGame(game.getId(), "host-token"); // ends game with guest as winner

        // second call should be idempotent
        GameGetDTO result = gameService.forfeitGame(game.getId(), "host-token");
        assertEquals(guestUser.getId(), result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
    }

    @Test
    void forfeitGame_invalidToken_throwsUnauthorized() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameService.forfeitGame(game.getId(), "bogus-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }


    @Test
    void forfeitDisconnectedPlayer_removesPlayerAndEndsGame() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        GameGetDTO result = gameService.forfeitDisconnectedPlayer(game.getId(), hostUser.getId());

        assertEquals(guestUser.getId(), result.getWinnerId());
        assertEquals(GameStatus.ENDED, result.getGameStatus());
    }

    @Test
    void forfeitDisconnectedPlayer_playerNotInActiveList_isIdempotent() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        // disconnect host first time
        gameService.forfeitDisconnectedPlayer(game.getId(), hostUser.getId());
        // second disconnect of same player should not throw
        assertDoesNotThrow(() ->
                gameService.forfeitDisconnectedPlayer(game.getId(), hostUser.getId()));
    }


    @Test
    void advanceTurn_rotatesBetweenTwoPlayers() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        Long firstTurn = game.getCurrentTurnUserId();

        gameService.advanceTurn(game);
        Long secondTurn = game.getCurrentTurnUserId();

        assertNotEquals(firstTurn, secondTurn);

        gameService.advanceTurn(game);
        assertEquals(firstTurn, game.getCurrentTurnUserId());
    }


    @Test
    void checkWinCondition_freshGame_neitherPlayerHasWon() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        // Pawns start in the middle of their respective sides, not at the goal
        assertFalse(gameService.checkWinCondition(game, hostUser.getId()));
        assertFalse(gameService.checkWinCondition(game, guestUser.getId()));
    }


    @Test
    void endGame_persistsWinnerAndStatus() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");

        gameService.endGame(game, guestUser.getId());

        Game persisted = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals(GameStatus.ENDED, persisted.getGameStatus());
        assertEquals(guestUser.getId(), persisted.getWinnerId());
    }
}