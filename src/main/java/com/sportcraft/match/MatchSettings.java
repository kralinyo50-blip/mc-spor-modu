package com.sportcraft.match;

/**
 * Plain, validated snapshot of every match-setup option shown in the GUI.
 * Sent whole over the network so client and server always agree on one object.
 */
public class MatchSettings {

    public static final int MIN_TEAM_SIZE = 1;
    public static final int MAX_TEAM_SIZE = 7;
    public static final int MIN_HALF_MINUTES = 1;
    public static final int MAX_HALF_MINUTES = 15;
    public static final int EXTRA_TIME_SECONDS = 120;

    private BallType sport = BallType.SOCCER;
    private FieldSize fieldSize = FieldSize.LEVEL_3;
    private int teamSize = 5;
    private MatchDifficulty difficulty = MatchDifficulty.NORMAL;
    private int halfMinutes = 3;
    private DrawRule drawRule = DrawRule.END_DRAW;
    private Formation homeFormation = Formation.F_2_1_1;
    private Formation awayFormation = Formation.F_2_1_1;
    // Club identity dressing each of the two sides; the home/away Team sides
    // themselves stay fixed, only their name, kit and seat colours change.
    private Club homeClub = Club.RED;
    private Club awayClub = Club.BLUE;

    public BallType getSport() {
        return sport;
    }

    public void setSport(BallType sport) {
        this.sport = sport;
    }

    public FieldSize getFieldSize() {
        return fieldSize;
    }

    public void setFieldSize(FieldSize fieldSize) {
        this.fieldSize = fieldSize;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = Math.max(MIN_TEAM_SIZE, Math.min(MAX_TEAM_SIZE, teamSize));
    }

    public MatchDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(MatchDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getHalfMinutes() {
        return halfMinutes;
    }

    public void setHalfMinutes(int halfMinutes) {
        this.halfMinutes = Math.max(MIN_HALF_MINUTES, Math.min(MAX_HALF_MINUTES, halfMinutes));
    }

    public DrawRule getDrawRule() {
        return drawRule;
    }

    public void setDrawRule(DrawRule drawRule) {
        this.drawRule = drawRule;
    }

    public Formation getHomeFormation() {
        return homeFormation;
    }

    public void setHomeFormation(Formation homeFormation) {
        this.homeFormation = homeFormation;
    }

    public Formation getAwayFormation() {
        return awayFormation;
    }

    public void setAwayFormation(Formation awayFormation) {
        this.awayFormation = awayFormation;
    }

    public Club getHomeClub() {
        return homeClub;
    }

    public void setHomeClub(Club homeClub) {
        this.homeClub = homeClub == null ? Club.RED : homeClub;
    }

    public Club getAwayClub() {
        return awayClub;
    }

    public void setAwayClub(Club awayClub) {
        this.awayClub = awayClub == null ? Club.BLUE : awayClub;
    }

    public int getHalfLengthSeconds() {
        return halfMinutes * 60;
    }

    public MatchSettings copy() {
        MatchSettings copy = new MatchSettings();
        copy.sport = sport;
        copy.fieldSize = fieldSize;
        copy.teamSize = teamSize;
        copy.difficulty = difficulty;
        copy.halfMinutes = halfMinutes;
        copy.drawRule = drawRule;
        copy.homeFormation = homeFormation;
        copy.awayFormation = awayFormation;
        copy.homeClub = homeClub;
        copy.awayClub = awayClub;
        return copy;
    }
}
