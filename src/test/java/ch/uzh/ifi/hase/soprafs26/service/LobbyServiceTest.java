package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LobbyServiceTest {

    @Mock
    private LobbyRepository lobbyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameService gameService;

    @Mock
    private ch.uzh.ifi.hase.soprafs26.websocket.GameWebSocketHandler gameWebSocketHandler;

    @InjectMocks
    private LobbyService lobbyService;

    private User testUser;
    private Lobby testLobby;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setToken("valid-token");

        testLobby = new Lobby();
        testLobby.setId(10L);
        testLobby.setName("Test Lobby");
        testLobby.setHostId(1L);
        testLobby.setMaxPlayers(2);
        testLobby.setCurrentPlayers(1);
        testLobby.setGameMode("Classic");
        testLobby.setMapTheme("Magic Forest");
        testLobby.setLobbyStatus(LobbyStatus.WAITING);
        testLobby.setInviteCode("ABCDEF");
        testLobby.getPlayerIds().add(1L);
    }

    @Test
    public void createLobby_validInput_success() {
        // given
        Lobby input = new Lobby();
        input.setName("My Lobby");
        input.setHostId(1L);
        input.setMaxPlayers(2);
        input.setGameMode("Classic");
        input.setMapTheme("Magic Forest");

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.save(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        // when
        Lobby created = lobbyService.createLobby(input, "valid-token");

        // then
        assertEquals(LobbyStatus.WAITING, created.getLobbyStatus());
        assertEquals(1, created.getCurrentPlayers());
        assertTrue(created.getPlayerIds().contains(1L));
        assertNotNull(created.getInviteCode());
        verify(lobbyRepository).save(any(Lobby.class));
        verify(lobbyRepository).flush();
    }

    @Test
    public void createLobby_invalidToken_throwsUnauthorized() {
        // given
        when(userRepository.findByToken("bad-token")).thenReturn(null);

        Lobby input = new Lobby();
        input.setName("Lobby");
        input.setHostId(1L);
        input.setMaxPlayers(2);
        input.setGameMode("Classic");

        // when / then
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.createLobby(input, "bad-token"));
    }

    @Test
    public void createLobby_hostIdMismatch_throwsForbidden() {
        // given — token belongs to user 1 but hostId says 99
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);

        Lobby input = new Lobby();
        input.setName("Lobby");
        input.setHostId(99L);
        input.setMaxPlayers(2);
        input.setGameMode("Classic");

        // when / then
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.createLobby(input, "valid-token"));
    }

    @Test
    public void createLobby_invalidPlayerCount_throwsBadRequest() {
        // given
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);

        Lobby input = new Lobby();
        input.setName("Lobby");
        input.setHostId(1L);
        input.setMaxPlayers(3); // only 2 or 4 are valid
        input.setGameMode("Classic");

        // when / then
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.createLobby(input, "valid-token"));
    }

    @Test
    public void getLobbyById_exists_returnsLobby() {
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        Lobby result = lobbyService.getLobbyById(10L);

        assertEquals(testLobby.getId(), result.getId());
    }

    @Test
    public void getLobbyById_notFound_throwsNotFound() {
        when(lobbyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.getLobbyById(99L));
    }

    @Test
    public void getLobbies_returnsAllLobbies() {
        when(lobbyRepository.findAll()).thenReturn(Arrays.asList(testLobby));

        List<Lobby> result = lobbyService.getLobbies();

        assertEquals(1, result.size());
    }

    @Test
    public void updateLobby_validInput_updatesFields() {
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));
        when(lobbyRepository.save(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        Lobby changes = new Lobby();
        changes.setName("New Name");
        changes.setGameMode("Chaos");

        lobbyService.updateLobby(10L, changes, "valid-token");

        assertEquals("New Name", testLobby.getName());
        assertEquals("Chaos", testLobby.getGameMode());
        verify(lobbyRepository).save(testLobby);
    }

    @Test
    public void updateLobby_notHost_throwsForbidden() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setToken("other-token");

        when(userRepository.findByToken("other-token")).thenReturn(otherUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        Lobby changes = new Lobby();
        changes.setName("Hacked Name");

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.updateLobby(10L, changes, "other-token"));
    }

    @Test
    public void joinLobbyById_validInput_addsPlayer() {
        User newUser = new User();
        newUser.setId(2L);
        newUser.setToken("token2");

        testLobby.setMaxPlayers(4);

        when(userRepository.findByToken("token2")).thenReturn(newUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));
        when(lobbyRepository.saveAndFlush(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        Lobby result = lobbyService.joinLobbyById(10L, "token2");

        assertTrue(result.getPlayerIds().contains(2L));
        assertEquals(2, result.getCurrentPlayers());
    }

    @Test
    public void joinLobbyById_lobbyFull_throwsBadRequest() {
        User newUser = new User();
        newUser.setId(2L);
        newUser.setToken("token2");

        // lobby already full (1/1 — max=1 is contrived but tests the guard)
        testLobby.setMaxPlayers(1);

        when(userRepository.findByToken("token2")).thenReturn(newUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.joinLobbyById(10L, "token2"));
    }

    @Test
    public void joinLobbyById_alreadyInLobby_returnsLobbyWithoutChange() {
        // user 1 is already in the lobby
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        Lobby result = lobbyService.joinLobbyById(10L, "valid-token");

        assertEquals(1, result.getCurrentPlayers()); // unchanged
        verify(lobbyRepository, never()).saveAndFlush(any());
    }

    @Test
    public void joinLobbyByInviteCode_validCode_addsPlayer() {
        User newUser = new User();
        newUser.setId(2L);
        newUser.setToken("token2");

        testLobby.setMaxPlayers(4);

        when(userRepository.findByToken("token2")).thenReturn(newUser);
        when(lobbyRepository.findByInviteCode("ABCDEF")).thenReturn(testLobby);
        when(lobbyRepository.saveAndFlush(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        Lobby result = lobbyService.joinLobbyByInviteCode("ABCDEF", "token2");

        assertTrue(result.getPlayerIds().contains(2L));
    }

    @Test
    public void joinLobbyByInviteCode_invalidCode_throwsNotFound() {
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findByInviteCode("WRONG")).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.joinLobbyByInviteCode("WRONG", "valid-token"));
    }

    @Test
    public void leaveLobby_validInput_removesPlayer() {
        // add a second player so lobby isn't deleted when first leaves
        testLobby.getPlayerIds().add(2L);
        testLobby.setCurrentPlayers(2);

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));
        when(lobbyRepository.saveAndFlush(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        lobbyService.leaveLobby(10L, "valid-token");

        assertFalse(testLobby.getPlayerIds().contains(1L));
        assertEquals(1, testLobby.getCurrentPlayers());
    }

    @Test
    public void leaveLobby_lastPlayer_deletesLobby() {
        // only user 1 is in the lobby
        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        lobbyService.leaveLobby(10L, "valid-token");

        verify(lobbyRepository).delete(testLobby);
    }

    @Test
    public void leaveLobby_hostLeaves_transfersHost() {
        User user2 = new User();
        user2.setId(2L);
        user2.setToken("token2");

        testLobby.getPlayerIds().add(2L);
        testLobby.setCurrentPlayers(2);
        // user 1 is the host

        when(userRepository.findByToken("valid-token")).thenReturn(testUser);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));
        when(lobbyRepository.saveAndFlush(any(Lobby.class))).thenAnswer(i -> i.getArgument(0));

        lobbyService.leaveLobby(10L, "valid-token");

        // host should have transferred to user 2
        assertEquals(2L, testLobby.getHostId());
    }

    @Test
    public void leaveLobby_userNotInLobby_throwsBadRequest() {
        User stranger = new User();
        stranger.setId(99L);
        stranger.setToken("stranger-token");

        when(userRepository.findByToken("stranger-token")).thenReturn(stranger);
        when(lobbyRepository.findById(10L)).thenReturn(Optional.of(testLobby));

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.leaveLobby(10L, "stranger-token"));
    }

    @Test
    public void generateInviteCode_returnsUpperCase6CharString() {
        String code = lobbyService.generateInviteCode();

        assertNotNull(code);
        assertEquals(6, code.length());
        assertEquals(code.toUpperCase(), code);
    }
}