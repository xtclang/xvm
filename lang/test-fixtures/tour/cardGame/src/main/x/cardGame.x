module cardGame {

    void run() {
        @Inject Console console;
        console.print("Starting Eights game.");
        Eights game = new Eights("Alice", "Bob");
        game.playGame();
    }
}
