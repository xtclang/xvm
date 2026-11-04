/**
 * Keeps the tally of the games each player has won. A service runs on a fiber of its own, apart
 * from the game's.
 */
service Scoreboard {
    /**
     * The games won, by the name of the player who won them.
     */
    private Map<String, Int> wins = new HashMap();

    /**
     * Record a win.
     *
     * @param name  the name of the player who won
     */
    void recordWin(String name) {
        wins.put(name, winsOf(name) + 1);
    }

    /**
     * @param name  the name of a player
     *
     * @return the number of games that player has won
     */
    Int winsOf(String name) = wins.getOrDefault(name, 0);
}
