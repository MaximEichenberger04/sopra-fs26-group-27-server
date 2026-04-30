package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class GifSearchResultDTO {

    private String id;
    private String previewUrl;
    private String gifUrl;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }

    public String getGifUrl() { return gifUrl; }
    public void setGifUrl(String gifUrl) { this.gifUrl = gifUrl; }
}
