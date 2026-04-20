package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class UserPatchDTO {
	private String displayName;
	private String username;
	private String password;
	private String avatarURL;
	private String preferredLanguage;
	private String biography;
	private String currentPassword;
	private String equippedBorder;
	private String equippedPawnSkin;

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

	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
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
}
