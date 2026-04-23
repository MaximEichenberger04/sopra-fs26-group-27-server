package ch.uzh.ifi.hase.soprafs26.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the UserResource REST resource.
 *
 * @see UserService
 */
@WebAppConfiguration
@SpringBootTest
public class UserServiceIntegrationTest {

	@Qualifier("userRepository")
	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserService userService;

	@BeforeEach
	public void setup() {
		userRepository.deleteAll();
	}

	private User createTestUser(String username, String displayName) {
		User user = new User();
		user.setUsername(username);
		user.setDisplayName(displayName);
		user.setPassword("TestPass1");
		return userService.createUser(user);
	}

	// ═══════════════════════════════════════════════
	// createUser
	// ═══════════════════════════════════════════════

	@Test
	public void createUser_validInputs_success() {
		User testUser = new User();
		testUser.setDisplayName("testName");
		testUser.setUsername("testUsername");
		testUser.setPassword("testPassword");

		User createdUser = userService.createUser(testUser);

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
		createTestUser("testUsername", "First User");

		User testUser2 = new User();
		testUser2.setDisplayName("testName2");
		testUser2.setUsername("testUsername");
		testUser2.setPassword("testPassword2");

		assertThrows(ResponseStatusException.class, () -> userService.createUser(testUser2));
	}

	// ═══════════════════════════════════════════════
	// loginUser
	// ═══════════════════════════════════════════════

	@Test
	public void loginUser_validCredentials_success() {
		User created = createTestUser("loginUser", "Login User");

		User loggedIn = userService.loginUser("loginUser", "TestPass1");

		assertEquals(UserStatus.ONLINE, loggedIn.getStatus());
		assertNotNull(loggedIn.getToken());
		assertEquals(created.getId(), loggedIn.getId());
	}

	@Test
	public void loginUser_wrongPassword_throwsUnauthorized() {
		createTestUser("loginUser", "Login User");

		assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("loginUser", "WrongPassword"));
	}

	@Test
	public void loginUser_unknownUser_throwsUnauthorized() {
		assertThrows(ResponseStatusException.class,
				() -> userService.loginUser("nobody", "password"));
	}

	@Test
	public void loginUser_generatesNewToken() {
		User created = createTestUser("tokenUser", "Token User");
		String firstToken = created.getToken();

		User loggedIn = userService.loginUser("tokenUser", "TestPass1");

		assertNotEquals(firstToken, loggedIn.getToken());
	}

	// ═══════════════════════════════════════════════
	// getUserById
	// ═══════════════════════════════════════════════

	@Test
	public void getUserById_existingUser_success() {
		User created = createTestUser("byIdUser", "ById User");

		User found = userService.getUserById(created.getId());

		assertEquals(created.getUsername(), found.getUsername());
	}

	@Test
	public void getUserById_nonExisting_throwsNotFound() {
		assertThrows(ResponseStatusException.class, () -> userService.getUserById(99999L));
	}

	// ═══════════════════════════════════════════════
	// validateToken
	// ═══════════════════════════════════════════════

	@Test
	public void validateToken_valid_noException() {
		User created = createTestUser("tokenValid", "Token Valid");

		assertDoesNotThrow(() -> userService.validateToken(created.getToken()));
	}

	@Test
	public void validateToken_null_throwsUnauthorized() {
		assertThrows(ResponseStatusException.class, () -> userService.validateToken(null));
	}

	@Test
	public void validateToken_invalid_throwsUnauthorized() {
		assertThrows(ResponseStatusException.class,
				() -> userService.validateToken("non-existent-token"));
	}

	// ═══════════════════════════════════════════════
	// updateUser
	// ═══════════════════════════════════════════════

	@Test
	public void updateUser_displayName_success() {
		User created = createTestUser("updateUser", "Old Name");

		User input = new User();
		input.setDisplayName("New Name");

		User updated = userService.updateUser(created.getId(), created.getToken(), input);

		assertEquals("New Name", updated.getDisplayName());
	}

	@Test
	public void updateUser_username_success() {
		User created = createTestUser("oldUsername", "Name");

		User input = new User();
		input.setUsername("newUsername");

		User updated = userService.updateUser(created.getId(), created.getToken(), input);

		assertEquals("newUsername", updated.getUsername());
	}

	@Test
	public void updateUser_duplicateUsername_throwsConflict() {
		createTestUser("existingName", "User One");
		User created = createTestUser("myName", "User Two");

		User input = new User();
		input.setUsername("existingName");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(created.getId(), created.getToken(), input));
		assertEquals(409, ex.getStatusCode().value());
	}

	@Test
	public void updateUser_biography_success() {
		User created = createTestUser("bioUser", "Bio User");

		User input = new User();
		input.setBiography("Hello world");

		User updated = userService.updateUser(created.getId(), created.getToken(), input);

		assertEquals("Hello world", updated.getBiography());
	}

	@Test
	public void updateUser_otherUsersProfile_throwsForbidden() {
		User user1 = createTestUser("user1", "User 1");
		User user2 = createTestUser("user2", "User 2");

		User input = new User();
		input.setDisplayName("Hacked");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(user1.getId(), user2.getToken(), input));
	}

	// ═══════════════════════════════════════════════
	// updateUser — password
	// ═══════════════════════════════════════════════

	@Test
	public void updateUser_passwordChange_success() {
		User created = createTestUser("pwUser", "PW User");

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("NewPass99");

		User updated = userService.updateUser(created.getId(), created.getToken(), input);

		assertEquals("NewPass99", updated.getPassword());
	}

	@Test
	public void updateUser_passwordChange_wrongCurrent_throwsForbidden() {
		User created = createTestUser("pwUser2", "PW User 2");

		User input = new User();
		input.setCurrentPassword("WrongCurrent");
		input.setPassword("NewPass99");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(created.getId(), created.getToken(), input));
	}

	@Test
	public void updateUser_passwordChange_tooShort_throwsBadRequest() {
		User created = createTestUser("pwShort", "PW Short");

		User input = new User();
		input.setCurrentPassword("TestPass1");
		input.setPassword("Ab1");

		assertThrows(ResponseStatusException.class,
				() -> userService.updateUser(created.getId(), created.getToken(), input));
	}

	// ═══════════════════════════════════════════════
	// updateUser — cosmetics equip
	// ═══════════════════════════════════════════════

	@Test
	public void updateUser_equipBorder_success() {
		User created = createTestUser("equipUser", "Equip User");

		User input = new User();
		input.setEquippedBorder("border-fire");

		User updated = userService.updateUser(created.getId(), created.getToken(), input);

		assertEquals("border-fire", updated.getEquippedBorder());
	}

	@Test
	public void updateUser_unequipBorder_setsNull() {
		User created = createTestUser("unequipUser", "Unequip User");

		User equipInput = new User();
		equipInput.setEquippedBorder("border-fire");
		userService.updateUser(created.getId(), created.getToken(), equipInput);

		User removeInput = new User();
		removeInput.setEquippedBorder("");

		User updated = userService.updateUser(created.getId(), created.getToken(), removeInput);

		assertNull(updated.getEquippedBorder());
	}

	// ═══════════════════════════════════════════════
	// buyCosmetic
	// ═══════════════════════════════════════════════

	@Test
	public void buyCosmetic_success() {
		User created = createTestUser("buyUser", "Buy User");

		User result = userService.buyCosmetic(created.getId(), created.getToken(), "border-crimson");

		assertEquals(700, result.getCoins());
		assertTrue(result.getOwnedCosmetics().contains("border-crimson"));
	}

	@Test
	public void buyCosmetic_multipleItems() {
		User created = createTestUser("multiUser", "Multi User");

		userService.buyCosmetic(created.getId(), created.getToken(), "border-crimson");
		User result = userService.buyCosmetic(created.getId(), created.getToken(), "pawn-lava");

		assertTrue(result.getOwnedCosmetics().contains("border-crimson"));
		assertTrue(result.getOwnedCosmetics().contains("pawn-lava"));
		assertEquals(300, result.getCoins());
	}

	@Test
	public void buyCosmetic_notEnoughCoins_throwsBadRequest() {
		User created = createTestUser("poorUser", "Poor User");

		// Buy items to drain coins: 300 + 300 + 500 = 1100, leaving -100 not possible
		userService.buyCosmetic(created.getId(), created.getToken(), "border-crimson"); // 1000 → 700
		userService.buyCosmetic(created.getId(), created.getToken(), "border-emerald"); // 700 → 400

		// border-rainbow costs 1000, but only 400 left
		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(created.getId(), created.getToken(), "border-rainbow"));
	}

	@Test
	public void buyCosmetic_alreadyOwned_throwsBadRequest() {
		User created = createTestUser("dupeUser", "Dupe User");

		userService.buyCosmetic(created.getId(), created.getToken(), "border-crimson");

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(created.getId(), created.getToken(), "border-crimson"));
	}

	@Test
	public void buyCosmetic_unknownItem_throwsBadRequest() {
		User created = createTestUser("unknownUser", "Unknown User");

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(created.getId(), created.getToken(), "nonexistent"));
	}

	@Test
	public void buyCosmetic_otherUser_throwsForbidden() {
		User user1 = createTestUser("buyer1", "Buyer 1");
		User user2 = createTestUser("buyer2", "Buyer 2");

		assertThrows(ResponseStatusException.class,
				() -> userService.buyCosmetic(user1.getId(), user2.getToken(), "border-crimson"));
	}

	// ═══════════════════════════════════════════════
	// logoutUser
	// ═══════════════════════════════════════════════

	@Test
	public void logoutUser_success() {
		User created = createTestUser("logoutUser", "Logout User");
		String tokenBeforeLogout = created.getToken();

		userService.logoutUser(tokenBeforeLogout);

		User afterLogout = userService.getUserById(created.getId());
		assertEquals(UserStatus.OFFLINE, afterLogout.getStatus());
		assertNotEquals(tokenBeforeLogout, afterLogout.getToken());
	}

	@Test
	public void logoutUser_invalidToken_throwsNotFound() {
		assertThrows(ResponseStatusException.class,
				() -> userService.logoutUser("nonexistent-token"));
	}
}