package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.GameStatus;
import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Game;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.Pawn;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.GameRepository;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.GameGetDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@WebAppConfiguration
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class GameServiceIntegrationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private GameStateCache gameStateCache;

    @Qualifier("gameRepository")
    @Autowired
    private GameRepository gameRepository;

    @Qualifier("lobbyRepository")
    @Autowired
    private LobbyRepository lobbyRepository;

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    // Named users for the main 2-player tests
    private User hostUser;
    private User guestUser;
    private Lobby lobby; // 2-player lobby for hostUser + guestUser

    // Extra users for 4-player test
    private User user3;
    private User user4;

    @BeforeEach
    public void setup() {
        gameRepository.deleteAll();
        lobbyRepository.deleteAll();
        userRepository.deleteAll();

        hostUser = createAndSaveUser("host", "host-token");
        guestUser = createAndSaveUser("guest", "guest-token");
        user3 = createAndSaveUser("user3", "token3");
        user4 = createAndSaveUser("user4", "token4");

        lobby = new Lobby();
        lobby.setName("Test Lobby");
        lobby.setLobbyStatus(LobbyStatus.WAITING);
        lobby.setHostId(hostUser.getId());
        lobby.setMaxPlayers(2);
        lobby.setCurrentPlayers(2);
        lobby.setGameMode("Classic");
        lobby.setInviteCode(UUID.randomUUID().toString());
        lobby.setMapTheme("medieval");
        lobby.setPlayerIds(Arrays.asList(hostUser.getId(), guestUser.getId()));
        lobby = lobbyRepository.saveAndFlush(lobby);
    }

    // ─────────────────────────────────────────────────────────────
    // createGameFromLobby
    // ─────────────────────────────────────────────────────────────

    @Test
    public void createGameFromLobby_twoPlayers_successfullyPersistsGameAndInitializesCache() {
        Game createdGame = gameService.createGameFromLobby(lobby.getId(), hostUser.getToken());

        assertNotNull(createdGame.getId());
        assertEquals(GameStatus.RUNNING, createdGame.getGameStatus());
        assertEquals(lobby.getId(), createdGame.getLobbyId());
        assertEquals(hostUser.getId(), createdGame.getCreatorId());
        assertEquals(hostUser.getId(), createdGame.getCurrentTurnUserId());
        assertEquals(9, createdGame.getSizeBoard());
        assertEquals(10, createdGame.getWallsPerPlayer());
        assertEquals("medieval", createdGame.getMapTheme());
        assertEquals(Arrays.asList(hostUser.getId(), guestUser.getId()), createdGame.getPlayerIds());
        assertEquals(Arrays.asList(hostUser.getId(), guestUser.getId()), createdGame.getActivePlayerIds());

        Game persistedGame = gameRepository.findById(createdGame.getId()).orElse(null);
        assertNotNull(persistedGame);
        assertEquals(GameStatus.RUNNING, persistedGame.getGameStatus());

        Lobby updatedLobby = lobbyRepository.findById(lobby.getId()).orElse(null);
        assertNotNull(updatedLobby);
        assertEquals(createdGame.getId(), updatedLobby.getGameId());

        List<Pawn> pawns = gameStateCache.getPawns(createdGame.getId());
        assertEquals(2, pawns.size());

        Pawn pawn1 = gameStateCache.getPawn(createdGame.getId(), hostUser.getId());
        Pawn pawn2 = gameStateCache.getPawn(createdGame.getId(), guestUser.getId());
        assertNotNull(pawn1);
        assertNotNull(pawn2);
        assertEquals(16, pawn1.getRow());
        assertEquals(8, pawn1.getCol());
        assertEquals(0, pawn2.getRow());
        assertEquals(8, pawn2.getCol());

        assertTrue(gameStateCache.getWalls(createdGame.getId()).isEmpty());
        assertEquals(17, gameStateCache.getWallGrid(createdGame.getId()).length);
        assertEquals(17, gameStateCache.getWallGrid(createdGame.getId())[0].length);
    }

    @Test
    public void createGameFromLobby_fourPlayers_setsCorrectWallBudgetAndStartPositions() {
        Lobby fourPlayerLobby = new Lobby();
        fourPlayerLobby.setName("Four Player Lobby");
        fourPlayerLobby.setLobbyStatus(LobbyStatus.WAITING);
        fourPlayerLobby.setHostId(hostUser.getId());
        fourPlayerLobby.setMaxPlayers(4);
        fourPlayerLobby.setCurrentPlayers(4);
        fourPlayerLobby.setGameMode("Classic");
        fourPlayerLobby.setInviteCode(UUID.randomUUID().toString());
        fourPlayerLobby.setPlayerIds(Arrays.asList(hostUser.getId(), guestUser.getId(), user3.getId(), user4.getId()));
        fourPlayerLobby.setMapTheme("Magic Forest");
        fourPlayerLobby = lobbyRepository.saveAndFlush(fourPlayerLobby);

        Game createdGame = gameService.createGameFromLobby(fourPlayerLobby.getId(), hostUser.getToken());

        assertNotNull(createdGame.getId());
        assertEquals(5, createdGame.getWallsPerPlayer());
        assertEquals("Magic Forest", createdGame.getMapTheme());

        Pawn pawn1 = gameStateCache.getPawn(createdGame.getId(), hostUser.getId());
        Pawn pawn2 = gameStateCache.getPawn(createdGame.getId(), guestUser.getId());
        Pawn pawn3 = gameStateCache.getPawn(createdGame.getId(), user3.getId());
        Pawn pawn4 = gameStateCache.getPawn(createdGame.getId(), user4.getId());

        assertNotNull(pawn1);
        assertNotNull(pawn2);
        assertNotNull(pawn3);
        assertNotNull(pawn4);
        assertEquals(16, pawn1.getRow()); assertEquals(8, pawn1.getCol());
        assertEquals(0,  pawn2.getRow()); assertEquals(8, pawn2.getCol());
        assertEquals(8,  pawn3.getRow()); assertEquals(16, pawn3.getCol());
        assertEquals(8,  pawn4.getRow()); assertEquals(0,  pawn4.getCol());
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

    // ─────────────────────────────────────────────────────────────
    // getGameById / buildGameGetDTO
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // forfeitGame
    // ─────────────────────────────────────────────────────────────

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
        gameService.forfeitGame(game.getId(), "host-token");
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

    // ─────────────────────────────────────────────────────────────
    // forfeitDisconnectedPlayer
    // ─────────────────────────────────────────────────────────────

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
        gameService.forfeitDisconnectedPlayer(game.getId(), hostUser.getId());
        assertDoesNotThrow(() ->
                gameService.forfeitDisconnectedPlayer(game.getId(), hostUser.getId()));
    }

    // ─────────────────────────────────────────────────────────────
    // advanceTurn
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // checkWinCondition
    // ─────────────────────────────────────────────────────────────

    @Test
    void checkWinCondition_freshGame_neitherPlayerHasWon() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        assertFalse(gameService.checkWinCondition(game, hostUser.getId()));
        assertFalse(gameService.checkWinCondition(game, guestUser.getId()));
    }

    // ─────────────────────────────────────────────────────────────
    // endGame
    // ─────────────────────────────────────────────────────────────

    @Test
    void endGame_persistsWinnerAndStatus() {
        Game game = gameService.createGameFromLobby(lobby.getId(), "host-token");
        gameService.endGame(game, guestUser.getId());
        Game persisted = gameRepository.findById(game.getId()).orElseThrow();
        assertEquals(GameStatus.ENDED, persisted.getGameStatus());
        assertEquals(guestUser.getId(), persisted.getWinnerId());
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private User createAndSaveUser(String username, String token) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("password");
        user.setDisplayName(username);
        user.setStatus(UserStatus.ONLINE);
        user.setCreationDate(LocalDate.now());
        user.setToken(token);
        return userRepository.saveAndFlush(user);
    }
}