package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;

public class UserPostDTO {

	private String displayName;
	private String username;
	private String password;
	private String avatarURL;
	private String preferredLanguage;
	private String biography;
	private int score;
	private int xp;
	private int level;
	private int coins;
	private String ownedCosmetics;
	private String equippedBorder;
	private String equippedPawnSkin;
	private LocalDate creationDate;
	private String token;
	private UserStatus status;
	private String currentPassword;

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
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

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
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

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}
}
