package ch.uzh.ifi.hase.soprafs26.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class UserRepositoryIntegrationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	private User persistedUser;

	@BeforeEach
	public void setup() {
		userRepository.deleteAll();

		User user = new User();
		user.setDisplayName("Test User");
		user.setUsername("testUser");
		user.setPassword("TestPass1");
		user.setStatus(UserStatus.ONLINE);
		user.setToken("test-token-123");
		user.setCreationDate(LocalDate.now());
		user.setCoins(1000);

		persistedUser = entityManager.persistAndFlush(user);
	}

	// ═══════════════════════════════════════════════
	// findByUsername
	// ═══════════════════════════════════════════════

	@Test
	public void findByUsername_existingUser_returnsUser() {
		User found = userRepository.findByUsername("testUser");

		assertNotNull(found);
		assertNotNull(found.getId());
		assertEquals("Test User", found.getDisplayName());
		assertEquals("testUser", found.getUsername());
		assertEquals("test-token-123", found.getToken());
		assertEquals(UserStatus.ONLINE, found.getStatus());
	}

	@Test
	public void findByUsername_nonExisting_returnsNull() {
		User found = userRepository.findByUsername("nonExistentUser");

		assertNull(found);
	}

	// ═══════════════════════════════════════════════
	// findByToken
	// ═══════════════════════════════════════════════

	@Test
	public void findByToken_existingToken_returnsUser() {
		User found = userRepository.findByToken("test-token-123");

		assertNotNull(found);
		assertEquals("testUser", found.getUsername());
	}

	@Test
	public void findByToken_nonExisting_returnsNull() {
		User found = userRepository.findByToken("nonexistent-token");

		assertNull(found);
	}

	// ═══════════════════════════════════════════════
	// findById
	// ═══════════════════════════════════════════════

	@Test
	public void findById_existingId_returnsUser() {
		assertTrue(userRepository.findById(persistedUser.getId()).isPresent());
	}

	@Test
	public void findById_nonExisting_returnsEmpty() {
		assertTrue(userRepository.findById(99999L).isEmpty());
	}

	// ═══════════════════════════════════════════════
	// save and update
	// ═══════════════════════════════════════════════

	@Test
	public void save_newUser_persistsAllFields() {
		User newUser = new User();
		newUser.setDisplayName("New User");
		newUser.setUsername("newUser");
		newUser.setPassword("NewPass1");
		newUser.setStatus(UserStatus.OFFLINE);
		newUser.setToken("new-token");
		newUser.setCreationDate(LocalDate.now());
		newUser.setCoins(500);
		newUser.setBiography("Bio text");
		newUser.setEquippedBorder("border-crimson");
		newUser.setEquippedPawnSkin("pawn-lava");
		newUser.setOwnedCosmetics("border-crimson,pawn-lava");

		User saved = userRepository.saveAndFlush(newUser);

		assertNotNull(saved.getId());
		assertEquals("New User", saved.getDisplayName());
		assertEquals(500, saved.getCoins());
		assertEquals("Bio text", saved.getBiography());
		assertEquals("border-crimson", saved.getEquippedBorder());
		assertEquals("pawn-lava", saved.getEquippedPawnSkin());
		assertEquals("border-crimson,pawn-lava", saved.getOwnedCosmetics());
	}

	@Test
	public void save_updateExisting_updatesFields() {
		persistedUser.setDisplayName("Updated Name");
		persistedUser.setCoins(500);
		persistedUser.setBiography("Updated bio");

		userRepository.saveAndFlush(persistedUser);

		User found = userRepository.findByUsername("testUser");

		assertEquals("Updated Name", found.getDisplayName());
		assertEquals(500, found.getCoins());
		assertEquals("Updated bio", found.getBiography());
	}

	// ═══════════════════════════════════════════════
	// cosmetic fields persistence
	// ═══════════════════════════════════════════════

	@Test
	public void cosmeticFields_persistCorrectly() {
		persistedUser.setOwnedCosmetics("border-crimson,pawn-lava");
		persistedUser.setEquippedBorder("border-crimson");
		persistedUser.setEquippedPawnSkin("pawn-lava");

		userRepository.saveAndFlush(persistedUser);

		User found = userRepository.findById(persistedUser.getId()).orElseThrow();

		assertEquals("border-crimson,pawn-lava", found.getOwnedCosmetics());
		assertEquals("border-crimson", found.getEquippedBorder());
		assertEquals("pawn-lava", found.getEquippedPawnSkin());
	}

	@Test
	public void cosmeticFields_nullByDefault() {
		assertNull(persistedUser.getOwnedCosmetics());
		assertNull(persistedUser.getEquippedBorder());
		assertNull(persistedUser.getEquippedPawnSkin());
	}

	// ═══════════════════════════════════════════════
	// multiple users
	// ═══════════════════════════════════════════════

	@Test
	public void findAll_multipleUsers_returnsAll() {
		User user2 = new User();
		user2.setDisplayName("User 2");
		user2.setUsername("user2");
		user2.setPassword("Pass2");
		user2.setStatus(UserStatus.OFFLINE);
		user2.setToken("token-2");
		user2.setCreationDate(LocalDate.now());

		entityManager.persistAndFlush(user2);

		assertEquals(2, userRepository.findAll().size());
	}
}