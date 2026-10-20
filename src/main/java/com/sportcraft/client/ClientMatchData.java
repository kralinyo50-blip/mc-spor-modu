package com.sportcraft.client;

import com.sportcraft.match.MatchState;
import com.sportcraft.match.Team;

/**
 * Client-side mirror of the last match snapshot received from the server.
 * It is display-only; nothing here is ever sent back as authoritative state.
 */
public final class ClientMatchData {

    public static final ClientMatchData INSTANCE = new ClientMatchData();

    private MatchState state = MatchState.IDLE;
    private int redScore;
    private int blueScore;
    private int remainingSeconds;
    private Team localTeam = Team.SPECTATOR;

    private ClientMatchData() {
    }

    public static void update(MatchState state, int redScore, int blueScore,
                              int remainingSeconds, Team localTeam) {
        INSTANCE.state = state;
        INSTANCE.redScore = redScore;
        INSTANCE.blueScore = blueScore;
        INSTANCE.remainingSeconds = remainingSeconds;
        INSTANCE.localTeam = localTeam;
    }

    public boolean isActive() {
        return state != MatchState.IDLE || redScore != 0 || blueScore != 0;
    }

    public MatchState getState() {
        return state;
    }

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public Team getLocalTeam() {
        return localTeam;
    }
}
