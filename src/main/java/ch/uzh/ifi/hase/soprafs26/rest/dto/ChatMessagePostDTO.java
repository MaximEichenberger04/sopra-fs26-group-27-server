package ch.uzh.ifi.hase.soprafs26.rest.dto;


public class ChatMessagePostDTO {

    private Long userId;
    private String username;
    private String text;
    private String gifUrl;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }
}
