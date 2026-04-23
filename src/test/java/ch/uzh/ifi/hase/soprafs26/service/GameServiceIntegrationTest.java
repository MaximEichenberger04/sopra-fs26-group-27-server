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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@WebAppConfiguration
@SpringBootTest
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

    private User user1;
    private User user2;
    private User user3;
    private User user4;

    @BeforeEach
    public void setup() {
        gameRepository.deleteAll();
        lobbyRepository.deleteAll();
        userRepository.deleteAll();

        user1 = createAndSaveUser("user1", "token1");
        user2 = createAndSaveUser("user2", "token2");
        user3 = createAndSaveUser("user3", "token3");
        user4 = createAndSaveUser("user4", "token4");
    }

    @Test
    public void createGameFromLobby_twoPlayers_successfullyPersistsGameAndInitializesCache() {
        // given
        Lobby lobby = new Lobby();
        lobby.setName("Test Lobby");
        lobby.setLobbyStatus(LobbyStatus.WAITING);
        lobby.setHostId(user1.getId());
        lobby.setMaxPlayers(2);
        lobby.setCurrentPlayers(2);
        lobby.setGameMode("Classic");
        lobby.setInviteCode("ABC123");
        lobby.setPlayerIds(Arrays.asList(user1.getId(), user2.getId()));
        lobby.setMapTheme("Classic");

        lobby = lobbyRepository.saveAndFlush(lobby);

        // when
        Game createdGame = gameService.createGameFromLobby(lobby.getId(), user1.getToken());

        // then: game entity
        assertNotNull(createdGame.getId());
        assertEquals(GameStatus.RUNNING, createdGame.getGameStatus());
        assertEquals(lobby.getId(), createdGame.getLobbyId());
        assertEquals(user1.getId(), createdGame.getCreatorId());
        assertEquals(user1.getId(), createdGame.getCurrentTurnUserId());
        assertEquals(9, createdGame.getSizeBoard());
        assertEquals(10, createdGame.getWallsPerPlayer());
        assertEquals("Classic", createdGame.getMapTheme());
        assertEquals(Arrays.asList(user1.getId(), user2.getId()), createdGame.getPlayerIds());
        assertEquals(Arrays.asList(user1.getId(), user2.getId()), createdGame.getActivePlayerIds());

        // then: persisted game exists
        Game persistedGame = gameRepository.findById(createdGame.getId()).orElse(null);
        assertNotNull(persistedGame);
        assertEquals(GameStatus.RUNNING, persistedGame.getGameStatus());

        // then: lobby was updated with gameId
        Lobby updatedLobby = lobbyRepository.findById(lobby.getId()).orElse(null);
        assertNotNull(updatedLobby);
        assertEquals(createdGame.getId(), updatedLobby.getGameId());

        // then: cache was initialized with correct pawn start positions
        List<Pawn> pawns = gameStateCache.getPawns(createdGame.getId());
        assertEquals(2, pawns.size());

        Pawn pawn1 = gameStateCache.getPawn(createdGame.getId(), user1.getId());
        Pawn pawn2 = gameStateCache.getPawn(createdGame.getId(), user2.getId());

        assertNotNull(pawn1);
        assertNotNull(pawn2);

        assertEquals(16, pawn1.getRow());
        assertEquals(8, pawn1.getCol());

        assertEquals(0, pawn2.getRow());
        assertEquals(8, pawn2.getCol());

        // then: no walls at game start
        assertTrue(gameStateCache.getWalls(createdGame.getId()).isEmpty());
        assertEquals(17, gameStateCache.getWallGrid(createdGame.getId()).length);
        assertEquals(17, gameStateCache.getWallGrid(createdGame.getId())[0].length);
    }

    @Test
    public void createGameFromLobby_fourPlayers_setsCorrectWallBudgetAndStartPositions() {
        // given
        Lobby lobby = new Lobby();
        lobby.setName("Four Player Lobby");
        lobby.setLobbyStatus(LobbyStatus.WAITING);
        lobby.setHostId(user1.getId());
        lobby.setMaxPlayers(4);
        lobby.setCurrentPlayers(4);
        lobby.setGameMode("Classic");
        lobby.setInviteCode("FOUR42");
        lobby.setPlayerIds(Arrays.asList(user1.getId(), user2.getId(), user3.getId(), user4.getId()));
        lobby.setMapTheme("Magic Forest");

        lobby = lobbyRepository.saveAndFlush(lobby);

        // when
        Game createdGame = gameService.createGameFromLobby(lobby.getId(), user1.getToken());

        // then
        assertNotNull(createdGame.getId());
        assertEquals(5, createdGame.getWallsPerPlayer());
        assertEquals("Magic Forest", createdGame.getMapTheme());

        Pawn pawn1 = gameStateCache.getPawn(createdGame.getId(), user1.getId());
        Pawn pawn2 = gameStateCache.getPawn(createdGame.getId(), user2.getId());
        Pawn pawn3 = gameStateCache.getPawn(createdGame.getId(), user3.getId());
        Pawn pawn4 = gameStateCache.getPawn(createdGame.getId(), user4.getId());

        assertNotNull(pawn1);
        assertNotNull(pawn2);
        assertNotNull(pawn3);
        assertNotNull(pawn4);

        assertEquals(16, pawn1.getRow());
        assertEquals(8, pawn1.getCol());

        assertEquals(0, pawn2.getRow());
        assertEquals(8, pawn2.getCol());

        assertEquals(8, pawn3.getRow());
        assertEquals(16, pawn3.getCol());

        assertEquals(8, pawn4.getRow());
        assertEquals(0, pawn4.getCol());
    }

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