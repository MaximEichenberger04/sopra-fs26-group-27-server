package ch.uzh.ifi.hase.soprafs26.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to
 * the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back
 * to the caller.
 */
@Service
@Transactional
public class UserService {

	private final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;

	private static final Map<String, Integer> COSMETIC_PRICES = Map.ofEntries(
			Map.entry("border-wood", 300), Map.entry("border-builder", 300),
			Map.entry("border-slime", 300), Map.entry("border-emerald", 500),
			Map.entry("border-ice", 500), Map.entry("border-knight", 500),
			Map.entry("border-fire", 800), Map.entry("border-shadow", 800),
			Map.entry("border-royal", 800), Map.entry("border-diamond", 1200),
			Map.entry("border-wizard", 1200), Map.entry("border-rainbow", 99999),
			Map.entry("pawn-lava", 400), Map.entry("pawn-ocean", 400),
			Map.entry("pawn-galaxy", 600), Map.entry("pawn-forest", 400),
			Map.entry("pawn-diamond", 800), Map.entry("pawn-gold", 1200),
			Map.entry("pawn-void", 600), Map.entry("pawn-rose", 400));

	public UserService(@Qualifier("userRepository") UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<User> getUsers() {
		return this.userRepository.findAll();
	}

	public List<User> getLeaderboard() {
		return this.userRepository.findAllByOrderByScoreDesc();
	}

	public User createUser(User newUser) {
		newUser.setToken(UUID.randomUUID().toString());
		newUser.setStatus(UserStatus.ONLINE);
		newUser.setCreationDate(LocalDate.now());
		newUser.setCoins(1000);
		checkIfUserExists(newUser);
		// saves the given entity but data is only persisted in the database once
		// flush() is called
		newUser = userRepository.save(newUser);
		userRepository.flush();

		log.debug("Created Information for User: {}", newUser);
		return newUser;
	}

	/**
	 * This is a helper method that will check the uniqueness criteria of the
	 * username and the name
	 * defined in the User entity. The method will do nothing if the input is unique
	 * and throw an error otherwise.
	 *
	 * @param userToBeCreated
	 * @throws org.springframework.web.server.ResponseStatusException
	 * @see User
	 */
	private void checkIfUserExists(User userToBeCreated) {
		User userByUsername = userRepository.findByUsername(userToBeCreated.getUsername());

		if (userByUsername != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The username provided is not unique. Therefore, the user could not be created!");
		}
	}

	public User loginUser(String username, String password) {
		User user = userRepository.findByUsername(username);

		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
		}

		if (!password.equals(user.getPassword())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong password");
		}

		user.setToken(UUID.randomUUID().toString());

		user.setStatus(UserStatus.ONLINE);
		userRepository.save(user);
		return user;
	}

	public User getUserById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	public void validateToken(String token) {
		if (token == null || userRepository.findByToken(token) == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token");
		}
	}

	public User updateUser(Long id, String token, User userInput) {
		User requestingUser = userRepository.findByToken(token);
		if (requestingUser == null || !requestingUser.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"You can only update your own user information");
		}

		User existingUser = getUserById(id);

		if (userInput.getUsername() != null && !userInput.getUsername().isBlank()) {
			if (!userInput.getUsername().equals(existingUser.getUsername())) {
				User conflict = userRepository.findByUsername(userInput.getUsername());
				if (conflict != null) { // Check if Username already exists
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"This username is already taken. Please choose another one.");
				}
				existingUser.setUsername(userInput.getUsername());
			}
		}

		if (userInput.getDisplayName() != null && !userInput.getDisplayName().isBlank()) {
			existingUser.setDisplayName(userInput.getDisplayName());
		}

		if (userInput.getBiography() != null) {
			existingUser.setBiography(userInput.getBiography());
		}

		if (userInput.getAvatarURL() != null) {
			existingUser.setAvatarURL(userInput.getAvatarURL());
		}

		if (userInput.getEquippedBorder() != null) {
			if (userInput.getEquippedBorder().isBlank()) {
				existingUser.setEquippedBorder(null);
			} else {
				existingUser.setEquippedBorder(userInput.getEquippedBorder());
			}
		}

		if (userInput.getEquippedPawnSkin() != null) {
			if (userInput.getEquippedPawnSkin().isBlank()) {
				existingUser.setEquippedPawnSkin(null);
			} else {
				existingUser.setEquippedPawnSkin(userInput.getEquippedPawnSkin());
			}
		}

		// Update password (requires current password for verification)
		if (userInput.getPassword() != null && !userInput.getPassword().isBlank()) {
			// 1. Check current password FIRST
			if (userInput.getCurrentPassword() == null ||
					!userInput.getCurrentPassword().equals(existingUser.getPassword())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN,
						"Wrong password filled in.");
			}
			// 2. Check new password requirements
			if (userInput.getPassword().length() < 8) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"New password must be at least 8 characters.");
			}
			if (!userInput.getPassword().matches(".*[A-Z].*")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"New password must contain at least one uppercase letter.");
			}
			if (!userInput.getPassword().matches(".*[0-9].*")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"New password must contain at least one number.");
			}
			// 3. Check it's different from current
			if (userInput.getPassword().equals(existingUser.getPassword())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"New password must be different from the current password.");
			}
			existingUser.setPassword(userInput.getPassword());
		}

		userRepository.save(existingUser);
		return existingUser;
	}

	public User buyCosmetic(Long id, String token, String cosmeticId) {
		User user = userRepository.findByToken(token);
		if (user == null || !user.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"You can only buy cosmetics for your own user account");
		}

		Integer price = COSMETIC_PRICES.get(cosmeticId);
		if (price == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown cosmetic");
		}

		String owned = user.getOwnedCosmetics() != null ? user.getOwnedCosmetics() : "";
		if (owned.contains(cosmeticId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already own this cosmetic");
		}

		if (user.getCoins() < price) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough coins");
		}

		user.setCoins(user.getCoins() - price);
		user.setOwnedCosmetics(owned.isEmpty() ? cosmeticId : owned + "," + cosmeticId);
		userRepository.save(user);
		return user;
	}

	public void logoutUser(String token) {

		User user = userRepository.findByToken(token);

		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
		}

		user.setStatus(UserStatus.OFFLINE);
		user.setToken(UUID.randomUUID().toString());

		userRepository.save(user);
		userRepository.flush();
	}

}