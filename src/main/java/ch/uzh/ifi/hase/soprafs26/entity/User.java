package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal User Representation
 * This class composes the internal representation of the user and defines how
 * the user is stored in the database.
 * Every variable will be mapped into a database field with the @Column
 * annotation
 * - nullable = false -> this cannot be left empty
 * - unique = true -> this value must be unqiue across the database -> composes
 * the primary key
 */
@Entity
@Table(name = "users")
public class User implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String displayName;

	@Column(nullable = true)
	private String biography;

	@Column(nullable = true, columnDefinition = "TEXT")
	private String avatarURL;

	@Column(nullable = false)
	private UserStatus status;

	@Column(nullable = true)
	private int score;

	@Column(nullable = true)
	private int xp;

	@Column(nullable = true)
	private int level;

	@Column(nullable = true)
	private String preferredLanguage;

	@Column(nullable = true)
	private int coins;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<UserCosmetic> cosmetics = new ArrayList<>();

	@Column(nullable = true)
	private String equippedBorder;

	@Column(nullable = true)
	private String equippedPawnSkin;

	@Column(nullable = true)
	private int totalGamesPlayed;

	@Column(nullable = true)
	private int totalWins;

	@Column(nullable = true)
	private int currentWinStreak;

	@Column(nullable = true)
	private int maxWinStreak;

	@Column(nullable = true, length = 1000)
	private String unlockedAchievements;

	@Transient
	private String currentPassword;

	@Column(nullable = false)
	private LocalDate creationDate;

	/**
	 * XP progress within current level (computed, not persisted).
	 * Used by the client to show progress bar.
	 */
	@Transient
	public int getXpCurrentLevelProgress() {
		int currentLevel = Math.max(1, this.level);
		// Total XP needed to reach current level: 130 * L * (L-1) / 2
		int xpForCurrentLevel = 130 * currentLevel * (currentLevel - 1) / 2;
		return this.xp - xpForCurrentLevel;
	}

	/**
	 * XP required to advance from current level to next level (computed).
	 * Formula: currentLevel * 130
	 */
	@Transient
	public int getXpRequiredForNextLevel() {
		return Math.max(1, this.level) * 130;
	}

	@Column(nullable = false, unique = true)
	private String token;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getBiography() {
		return biography;
	}

	public void setBiography(String biography) {
		this.biography = biography;
	}

	public String getAvatarURL() {
		return avatarURL;
	}

	public void setAvatarURL(String avatarURL) {
		this.avatarURL = avatarURL;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public int getXp() {
		return xp;
	}

	public void setXp(int xp) {
		this.xp = xp;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
	}

	public int getCoins() {
		return coins;
	}

	public void setCoins(int coins) {
		this.coins = coins;
	}

	/**
	 * Returns the owned cosmetics as a backward-compatible comma-separated string,
	 * or null when none are owned. Used by DTOs, tests, and the mapper.
	 */
	public String getOwnedCosmetics() {
		if (cosmetics == null || cosmetics.isEmpty()) {
			return null;
		}
		return cosmetics.stream()
				.map(UserCosmetic::getCosmeticId)
				.collect(Collectors.joining(","));
	}

	/**
	 * Backward-compatible helper. Parses a comma-separated cosmetic string and
	 * replaces the current collection. Passing null or blank clears all owned
	 * cosmetics.
	 */
	public void setOwnedCosmetics(String ownedCosmetics) {
		if (cosmetics == null) {
			cosmetics = new ArrayList<>();
		}
		cosmetics.clear();
		if (ownedCosmetics == null || ownedCosmetics.isBlank()) {
			return;
		}
		for (String id : ownedCosmetics.split(",")) {
			String trimmed = id.trim();
			if (!trimmed.isEmpty()) {
				UserCosmetic uc = new UserCosmetic(this, trimmed);
				cosmetics.add(uc);
			}
		}
	}

	/** Direct access to the cosmetics collection (for service-layer use). */
	public List<UserCosmetic> getCosmetics() {
		return cosmetics;
	}

	public void addCosmetic(String cosmeticId) {
		if (cosmetics == null) {
			cosmetics = new ArrayList<>();
		}
		cosmetics.add(new UserCosmetic(this, cosmeticId));
	}

	public String getEquippedBorder() {
		return equippedBorder;
	}

	public void setEquippedBorder(String equippedBorder) {
		this.equippedBorder = equippedBorder;
	}

	public String getEquippedPawnSkin() {
		return equippedPawnSkin;
	}

	public void setEquippedPawnSkin(String equippedPawnSkin) {
		this.equippedPawnSkin = equippedPawnSkin;
	}

	public LocalDate getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(LocalDate creationDate) {
		this.creationDate = creationDate;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public int getTotalGamesPlayed() {
		return totalGamesPlayed;
	}

	public void setTotalGamesPlayed(int totalGamesPlayed) {
		this.totalGamesPlayed = totalGamesPlayed;
	}

	public int getTotalWins() {
		return totalWins;
	}

	public void setTotalWins(int totalWins) {
		this.totalWins = totalWins;
	}

	public int getCurrentWinStreak() {
		return currentWinStreak;
	}

	public void setCurrentWinStreak(int currentWinStreak) {
		this.currentWinStreak = currentWinStreak;
	}

	public int getMaxWinStreak() {
		return maxWinStreak;
	}

	public void setMaxWinStreak(int maxWinStreak) {
		this.maxWinStreak = maxWinStreak;
	}

	public String getUnlockedAchievements() {
		return unlockedAchievements;
	}

	public void setUnlockedAchievements(String unlockedAchievements) {
		this.unlockedAchievements = unlockedAchievements;
	}

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}
}