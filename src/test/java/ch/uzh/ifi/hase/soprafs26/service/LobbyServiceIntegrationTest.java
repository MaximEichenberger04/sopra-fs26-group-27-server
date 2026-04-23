package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.constant.LobbyStatus;
import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.Lobby;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.LobbyRepository;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LobbyServiceIntegrationTest
 *
 * Tests LobbyService wired against a real in-memory H2 database.
 * Covers the full create → join → leave lifecycle.
 */
@WebAppConfiguration
@SpringBootTest
@Transactional
public class LobbyServiceIntegrationTest {

    @Qualifier("lobbyRepository")
    @Autowired
    private LobbyRepository lobbyRepository;

    @Qualifier("userRepository")
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private UserService userService;

    private User hostUser;
    private User guestUser;

    @BeforeEach
    public void setup() {
        lobbyRepository.deleteAll();
        userRepository.deleteAll();

        // Create a host user via UserService so token etc. are set correctly
        User rawHost = new User();
        rawHost.setUsername("host_user");
        rawHost.setDisplayName("Host");
        rawHost.setPassword("password");
        hostUser = userService.createUser(rawHost);

        User rawGuest = new User();
        rawGuest.setUsername("guest_user");
        rawGuest.setDisplayName("Guest");
        rawGuest.setPassword("password");
        guestUser = userService.createUser(rawGuest);
    }

    // helper — builds a minimal valid lobby input
    private Lobby buildLobbyInput() {
        Lobby lobby = new Lobby();
        lobby.setName("Integration Lobby");
        lobby.setHostId(hostUser.getId());
        lobby.setMaxPlayers(2);
        lobby.setGameMode("Classic");
        lobby.setMapTheme("Magic Forest");
        return lobby;
    }

    @Test
    public void createLobby_validInputs_success() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());

        assertNotNull(created.getId());
        assertEquals("Integration Lobby", created.getName());
        assertEquals(LobbyStatus.WAITING, created.getLobbyStatus());
        assertEquals(1, created.getCurrentPlayers());
        assertTrue(created.getPlayerIds().contains(hostUser.getId()));
        assertNotNull(created.getInviteCode());
        assertEquals(6, created.getInviteCode().length());
    }

    @Test
    public void createLobby_invalidToken_throwsUnauthorized() {
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.createLobby(buildLobbyInput(), "totally-wrong-token"));
    }

    @Test
    public void createLobby_blankName_throwsBadRequest() {
        Lobby lobby = buildLobbyInput();
        lobby.setName("  ");

        assertThrows(ResponseStatusException.class,
                () -> lobbyService.createLobby(lobby, hostUser.getToken()));
    }

    @Test
    public void getLobbyById_existingLobby_returnsLobby() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());

        Lobby fetched = lobbyService.getLobbyById(created.getId());

        assertEquals(created.getId(), fetched.getId());
        assertEquals("Integration Lobby", fetched.getName());
    }

    @Test
    public void getLobbyById_nonExistent_throwsNotFound() {
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.getLobbyById(99999L));
    }

    @Test
    public void joinLobbyById_validGuest_addsToLobby() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());

        // lobby has maxPlayers=2, currently 1 → guest can join
        Lobby joined = lobbyService.joinLobbyById(created.getId(), guestUser.getToken());

        assertEquals(2, joined.getCurrentPlayers());
        assertTrue(joined.getPlayerIds().contains(guestUser.getId()));
    }

    @Test
    public void joinLobbyByInviteCode_validCode_addsGuest() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());

        Lobby joined = lobbyService.joinLobbyByInviteCode(created.getInviteCode(), guestUser.getToken());

        assertTrue(joined.getPlayerIds().contains(guestUser.getId()));
    }

    @Test
    public void joinLobbyById_lobbyFull_throwsBadRequest() {
        // create a 2-player lobby and fill it with host + guest
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());
        lobbyService.joinLobbyById(created.getId(), guestUser.getToken());

        // third user can't join
        User thirdUser = new User();
        thirdUser.setUsername("third_user");
        thirdUser.setDisplayName("Third");
        thirdUser.setPassword("password");
        thirdUser = userService.createUser(thirdUser);

        final User finalThird = thirdUser;
        final Long lobbyId = created.getId();
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.joinLobbyById(lobbyId, finalThird.getToken()));
    }

    @Test
    public void leaveLobby_guestLeaves_lobbyStillExists() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());
        lobbyService.joinLobbyById(created.getId(), guestUser.getToken());

        lobbyService.leaveLobby(created.getId(), guestUser.getToken());

        Lobby after = lobbyService.getLobbyById(created.getId());
        assertEquals(1, after.getCurrentPlayers());
        assertFalse(after.getPlayerIds().contains(guestUser.getId()));
    }

    @Test
    public void leaveLobby_lastPlayerLeaves_lobbyDeleted() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());

        lobbyService.leaveLobby(created.getId(), hostUser.getToken());

        final Long id = created.getId();
        assertThrows(ResponseStatusException.class,
                () -> lobbyService.getLobbyById(id));
    }

    @Test
    public void leaveLobby_hostLeaves_hostTransferred() {
        Lobby created = lobbyService.createLobby(buildLobbyInput(), hostUser.getToken());
        lobbyService.joinLobbyById(created.getId(), guestUser.getToken());

        lobbyService.leaveLobby(created.getId(), hostUser.getToken());

        Lobby after = lobbyService.getLobbyById(created.getId());
        assertEquals(guestUser.getId(), after.getHostId());
    }
}