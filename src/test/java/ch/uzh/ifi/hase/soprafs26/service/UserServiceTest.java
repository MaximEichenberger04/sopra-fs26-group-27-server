package ch.uzh.ifi.hase.soprafs26.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	private User testUser;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);

		testUser = new User();
		testUser.setId(1L);
		testUser.setDisplayName("testName");
		testUser.setUsername("testUsername");
		testUser.setPassword("TestPass1");
		testUser.setToken("test-token");
		testUser.setStatus(UserStatus.ONLINE);
		testUser.setCreationDate(LocalDate.now());
		testUser.setCoins(1000);

		when(userRepository.save(any())).thenReturn(testUser);
	}

	// createUSer
	@Test
	public void createUser_validInputs_success() {
		User createdUser = userService.createUser(testUser);

		verify(userRepository, times(1)).save(any());
		verify(userRepository, times(1)).flush();

		assertEquals(testUser.getId(), createdUser.getId());
		assertEquals(testUser.getDisplayName(), createdUser.getDisplayName());
		assertEquals(testUser.getUsername(), createdUser.getUsername());
		assertNotNull(createdUser.getToken());
		assertEquals(UserStatus.ONLINE, createdUser.getStatus());
		assertNotNull(createdUser.getCreationDate());
		assertEquals(1000, createdUser.getCoins());
	}

	@Test
	public void createUser_duplicateUsername_throwsException() {
		when(userRepository.findByUsername("testUsername")).thenReturn(testUser);

		assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser));
	}

	@Test
	public void createUser_setsTokenAndStatus() {
		User newUser = new User();
		newUser.setUsername("newUser");
		newUser.setDisplayName("New");
		newUser.setPassword("Pass1234");

		when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		User created = userService.createUser(newUser);

		assertNotNull(created.getToken());
		assertEquals(UserStatus.ONLINE, created.getStatus());
		assertEquals(LocalDate.now(), created.getCreationDate());
		assertEquals(1000, created.getCoins());
	}

	// getUsers
	@Test
	public void getUsers_returnsAllUsers() {
		User user2 = new User();
		user2.setId(2L);
		user2.setUsername("user2");

		when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, user2));

		List<User> users = userService.getUsers();

		assertEquals(2, users.size());
	}

	@Test
	public void getUsers_emptyList() {
		when(userRepository.findAll()).thenReturn(List.of());

		List<User> users = userService.getUsers();

		assertTrue(users.isEmpty());
	}

	// getUSerbyId
	@Test
	public void getUserById_existingUser_returnsUser() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User found = userService.getUserById(1L);

		assertEquals(testUser.getId(), found.getId());
		assertEquals(testUser.getUsername(), found.getUsername());
	}

	@Test
	public void getUserById_nonExistingUser_throwsNotFound() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class, () -> userService.getUserById(99L));
	}

	// loginUser
	@Test
	public void loginUser_validCredentials_success() {
		when(userRepository.findByUsername("testUsername")).thenReturn(testUser);

		User loggedIn = userService.loginUser("testUsername", "TestPass1");

		assertEquals(UserStatus.ONLINE, loggedIn.getStatus());
		assertNotNull(loggedIn.getToken());
		verify(userRepository).save(testUser);
	}

	@Test
	public void loginUser_unknownUsername_throwsUnauthorized() {
		when(userRepository.findByUsername("unknown")).thenReturn(null);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("unknown", "password"));
		assertEquals(401, ex.getStatusCode().value());
	}

	@Test
	public void loginUser_wrongPassword_throwsUnauthorized() {
		when(userRepository.findByUsername("testUsername")).thenReturn(testUser);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("testUsername", "wrongPassword"));
		assertEquals(401, ex.getStatusCode().value());
	}

	@Test
	public void loginUser_generatesNewToken() {
		when(userRepository.findByUsername("testUsername")).thenReturn(testUser);
		String oldToken = testUser.getToken();

		userService.loginUser("testUsername", "TestPass1");

		assertNotEquals(oldToken, testUser.getToken());
	}

	// validateToken
	@Test
	public void validateToken_validToken_noException() {
		when(userRepository.findByToken("valid-token")).thenReturn(testUser);

		assertDoesNotThrow(() -> userService.validateToken("valid-token"));
	}

	@Test
	public void validateToken_nullToken_throwsUnauthorized() {
		assertThrows(ResponseStatusException.class, () -> userService.validateToken(null));
	}

	@Test
	public void validateToken_invalidToken_throwsUnauthorized() {
		when(userRepository.findByToken("bad-token")).thenReturn(null);

		assertThrows(ResponseStatusException.class, () -> userService.validateToken("bad-token"));
	}

	// updateUser - profile fields
	@Test
	public void updateUser_updateDisplayName_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setDisplayName("NewName");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("NewName", updated.getDisplayName());
	}

	@Test
	public void updateUser_updateUsername_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
		when(userRepository.findByUsername("newUsername")).thenReturn(null);

		User input = new User();
		input.setUsername("newUsername");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("newUsername", updated.getUsername());
	}

	@Test
	public void updateUser_duplicateUsername_throwsConflict() {
		User otherUser = new User();
		otherUser.setId(2L);
		otherUser.setUsername("taken");

		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
		when(userRepository.findByUsername("taken")).thenReturn(otherUser);

		User input = new User();
		input.setUsername("taken");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
		assertEquals(409, ex.getStatusCode().value());
	}

	@Test
	public void updateUser_sameUsername_noConflict() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
		when(userRepository.findByUsername("testUsername")).thenReturn(testUser);

		User input = new User();
		input.setUsername("testUsername");

		assertDoesNotThrow(() -> userService.updateUser(1L, "test-token", input));
	}

	@Test
	public void updateUser_updateBiography_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setBiography("New bio");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("New bio", updated.getBiography());
	}

	@Test
	public void updateUser_updateAvatarURL_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setAvatarURL("data:image/png;base64,abc123");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("data:image/png;base64,abc123", updated.getAvatarURL());
	}

	@Test
	public void updateUser_wrongUser_throwsForbidden() {
		User otherUser = new User();
		otherUser.setId(2L);
		otherUser.setToken("other-token");

		when(userRepository.findByToken("other-token")).thenReturn(otherUser);

		User input = new User();
		input.setDisplayName("hacked");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "other-token", input));
		assertEquals(403, ex.getStatusCode().value());
	}

	@Test
	public void updateUser_invalidToken_throwsForbidden() {
		when(userRepository.findByToken("bad-token")).thenReturn(null);

		User input = new User();

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "bad-token", input));
	}

	// updateUser - password change
	@Test
	public void updateUser_changePassword_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("NewPass99");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("NewPass99", updated.getPassword());
	}

	@Test
	public void updateUser_changePassword_wrongCurrentPassword_throwsForbidden() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("WrongCurrent1");
		input.setPassword("NewPass99");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
		assertEquals(403, ex.getStatusCode().value());
	}

	@Test
	public void updateUser_changePassword_noCurrentPassword_throwsForbidden() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setPassword("NewPass99");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
	}

	@Test
	public void updateUser_changePassword_tooShort_throwsBadRequest() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("Sh1");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
		assertEquals(400, ex.getStatusCode().value());
	}

	@Test
	public void updateUser_changePassword_noUppercase_throwsBadRequest() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("newpass99");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
	}

	@Test
	public void updateUser_changePassword_noNumber_throwsBadRequest() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("NewPassNoNum");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
	}

	@Test
	public void updateUser_changePassword_sameAsCurrent_throwsBadRequest() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("TestPass1");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(1L, "test-token", input));
	}

	// updateUser - cosmetics
	@Test
	public void updateUser_equipBorder_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setEquippedBorder("border-crimson");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("border-crimson", updated.getEquippedBorder());
	}

	@Test
	public void updateUser_unequipBorder_setsNull() {
		testUser.setEquippedBorder("border-crimson");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setEquippedBorder("");

		User updated = userService.updateUser(1L, "test-token", input);

		assertNull(updated.getEquippedBorder());
	}

	@Test
	public void updateUser_equipPawnSkin_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setEquippedPawnSkin("pawn-galaxy");

		User updated = userService.updateUser(1L, "test-token", input);

		assertEquals("pawn-galaxy", updated.getEquippedPawnSkin());
	}

	@Test
	public void updateUser_unequipPawnSkin_setsNull() {
		testUser.setEquippedPawnSkin("pawn-galaxy");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

		User input = new User();
		input.setEquippedPawnSkin("");

		User updated = userService.updateUser(1L, "test-token", input);

		assertNull(updated.getEquippedPawnSkin());
	}

	// buyCosmetic
	@Test
	public void buyCosmetic_validPurchase_success() {
		testUser.setCoins(1000);
		testUser.setOwnedCosmetics("");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		User result = userService.buyCosmetic(1L, "test-token", "border-crimson");

		assertEquals(700, result.getCoins());
		assertTrue(result.getOwnedCosmetics().contains("border-crimson"));
	}

	@Test
	public void buyCosmetic_appendsToExisting() {
		testUser.setCoins(1000);
		testUser.setOwnedCosmetics("border-crimson");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		User result = userService.buyCosmetic(1L, "test-token", "border-emerald");

		assertEquals("border-crimson,border-emerald", result.getOwnedCosmetics());
	}

	@Test
	public void buyCosmetic_firstPurchase_nullOwnedCosmetics() {
		testUser.setCoins(1000);
		testUser.setOwnedCosmetics(null);
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		User result = userService.buyCosmetic(1L, "test-token", "pawn-lava");

		assertEquals("pawn-lava", result.getOwnedCosmetics());
		assertEquals(600, result.getCoins());
	}

	@Test
	public void buyCosmetic_notEnoughCoins_throwsBadRequest() {
		testUser.setCoins(100);
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(1L, "test-token", "border-rainbow"));
		assertEquals(400, ex.getStatusCode().value());
	}

	@Test
	public void buyCosmetic_alreadyOwned_throwsBadRequest() {
		testUser.setCoins(1000);
		testUser.setOwnedCosmetics("border-crimson");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(1L, "test-token", "border-crimson"));
	}

	@Test
	public void buyCosmetic_unknownCosmetic_throwsBadRequest() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(1L, "test-token", "nonexistent-item"));
	}

	@Test
	public void buyCosmetic_wrongUser_throwsForbidden() {
		User otherUser = new User();
		otherUser.setId(2L);
		when(userRepository.findByToken("test-token")).thenReturn(otherUser);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(1L, "test-token", "border-crimson"));
		assertEquals(403, ex.getStatusCode().value());
	}

	@Test
	public void buyCosmetic_invalidToken_throwsForbidden() {
		when(userRepository.findByToken("bad-token")).thenReturn(null);

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(1L, "bad-token", "border-crimson"));
	}

	@Test
	public void buyCosmetic_expensiveItem_deductsCorrectly() {
		testUser.setCoins(1200);
		testUser.setOwnedCosmetics("");
		when(userRepository.findByToken("test-token")).thenReturn(testUser);

		User result = userService.buyCosmetic(1L, "test-token", "pawn-gold");

		assertEquals(0, result.getCoins());
	}

	// logoutUser
	@Test
	public void logoutUser_validToken_success() {
		when(userRepository.findByToken("test-token")).thenReturn(testUser);
		String oldToken = testUser.getToken();

		userService.logoutUser("test-token");

		assertEquals(UserStatus.OFFLINE, testUser.getStatus());
		assertNotEquals(oldToken, testUser.getToken());
		verify(userRepository).save(testUser);
		verify(userRepository).flush();
	}

	@Test
	public void logoutUser_invalidToken_throwsNotFound() {
		when(userRepository.findByToken("bad-token")).thenReturn(null);

		assertThrows(ResponseStatusException.class,
				() -> userService.logoutUser("bad-token"));
	}
}
