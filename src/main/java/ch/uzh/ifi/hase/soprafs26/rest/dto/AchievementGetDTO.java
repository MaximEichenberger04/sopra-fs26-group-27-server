package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class AchievementGetDTO {

    private String id;
    private String name;
    private String description;
    private int coinReward;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCoinReward() { return coinReward; }
    public void setCoinReward(int coinReward) { this.coinReward = coinReward; }
}
