package ch.uzh.ifi.hase.soprafs26.entity;

public enum AchievementDefinition {

    // Progress-based
    FIRST_WIN("first_win", "First Blood", "Win your first game", 300),
    WIN_5("win_5", "On a Roll", "Win 5 games", 500),
    WIN_15("win_15", "Champion", "Win 15 games", 1000),
    WIN_30("win_30", "Legend", "Win 30 games", 1500),
    STREAK_3("streak_3", "Hat Trick", "Win 3 games in a row", 200),
    STREAK_5("streak_5", "On Fire", "Win 5 games in a row", 400),
    STREAK_10("streak_10", "Unstoppable", "Reach a win streak of 10", 800),
    PLAYED_5("played_5", "Getting Started", "Play 5 games", 100),
    PLAYED_15("played_15", "Dedicated", "Play 15 games", 300),
    PLAYED_30("played_30", "Veteran", "Play 30 games", 600),
    LEVEL_10("level_10", "Rising Star", "Reach level 10", 200),
    LEVEL_25("level_25", "Elite", "Reach level 25", 500),

    // Per-game-based
    WIN_NO_WALLS("win_no_walls", "The Wanderer", "Win a game without placing any walls", 300),
    WIN_QUICK("win_quick", "Lightning", "Win a game in 15 moves or fewer", 300),
    WIN_MAX_WALLS("win_max_walls", "The Wall", "Win a game using 8 or more walls", 250),
    WIN_4PLAYER("win_4player", "Battle Royale", "Win a 4-player game", 350);

    private final String id;
    private final String name;
    private final String description;
    private final int coinReward;

    AchievementDefinition(String id, String name, String description, int coinReward) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.coinReward = coinReward;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCoinReward() { return coinReward; }

    public static AchievementDefinition fromId(String id) {
        for (AchievementDefinition a : values()) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }
}
