package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class UserStatisticsGetDTO {

    private int totalGames;
    private int wins;
    private int losses;
    private double winLossRatio;   // wins / totalGames (0 when no games played)
    private String mostPlayedGameMode; // null when no games played

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public double getWinLossRatio() { return winLossRatio; }
    public void setWinLossRatio(double winLossRatio) { this.winLossRatio = winLossRatio; }

    public String getMostPlayedGameMode() { return mostPlayedGameMode; }
    public void setMostPlayedGameMode(String mostPlayedGameMode) { this.mostPlayedGameMode = mostPlayedGameMode; }
}