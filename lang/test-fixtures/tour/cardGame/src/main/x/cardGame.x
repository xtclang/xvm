module cardGame {

    /**
     * Play a game between two players, Alice and Bob, or the two that the arguments name.
     */
    void run(String[] args = []) {
        @Inject Console console;
        console.print("Starting Eights game.");
        Eights game = new Eights(args.size > 0 ? args[0] : "Alice", args.size > 1 ? args[1] : "Bob");
        game.playGame();
    }
}
