package com.yamakotaro.sulfursoccer.match;

/** Outcome of MatchManager.join - either an error message key, or the team the player was auto-assigned to. */
public record JoinResult(String errorKey, char team) {

    public static JoinResult error(String errorKey) {
        return new JoinResult(errorKey, ' ');
    }

    public static JoinResult success(char team) {
        return new JoinResult(null, team);
    }

    public boolean isError() {
        return errorKey != null;
    }
}
