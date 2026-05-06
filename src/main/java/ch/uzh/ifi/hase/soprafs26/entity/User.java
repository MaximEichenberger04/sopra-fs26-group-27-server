package ch.uzh.ifi.hase.soprafs26.entity;

import jakarta.persistence.*;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;

import java.io.Serializable;
import java.time.LocalDate;

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

	@Column(nullable = true)
	private String ownedCosmetics;

	@Column(nullable = true)
	private String equippedBorder;

	@Column(nullable = true)
	private String equippedPawnSkin;

	@Column(nullable = true)
	private int totalGamesPlayed;

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

	public String getOwnedCosmetics() {
		return ownedCosmetics;
	}

	public void setOwnedCosmetics(String ownedCosmetics) {
		this.ownedCosmetics = ownedCosmetics;
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
