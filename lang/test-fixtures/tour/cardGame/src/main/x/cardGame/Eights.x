/**
 * A game of Crazy Eights between two players: each plays a card that matches the top of the
 * discard pile in suit or rank, an eight matches anything, and the first to empty their hand wins.
 */
class Eights {
    public Player one;
    public Player two;
    public Hand   drawPile;
    public Hand   discardPile;

    /**
     * The tally of the games won, which outlives a game.
     */
    public Scoreboard scoreboard = new Scoreboard();

    construct(String name1, String name2) {
        this.one         = new Player(name1);
        this.two         = new Player(name2);
        this.drawPile    = new Hand(GameConstants.DRAW_PILE_NAME);
        this.discardPile = new Hand(GameConstants.DISCARD_PILE_NAME);
    } finally {
        two.strategy = new SaveEights();
        initializeGame();
    }

    private void initializeGame() {
        Deck deck = new Deck(GameConstants.DECK_NAME);
        deck.shuffle();
        deck.deal(one.hand, GameConstants.INITIAL_HAND_SIZE);
        deck.deal(two.hand, GameConstants.INITIAL_HAND_SIZE);
        deck.dealAll(drawPile);
        if (Card card := drawCard()) {
            discardPile.addCard(card);
        }
    }

    /**
     * @return True iff a player has played their last card
     */
    Boolean isDone() {
        return one.hand.isEmpty() || two.hand.isEmpty();
    }

    void reshuffle() {
        if (discardPile.size() <= GameConstants.MIN_DISCARD_PILE_FOR_RESHUFFLE) {
            return;
        }
        if (Card top := discardPile.popCard()) {
            discardPile.dealAll(drawPile);
            discardPile.addCard(top);
            drawPile.shuffle();
        }
    }

    /**
     * Draw the top card of the draw pile, reshuffling the discard pile into it when it is empty.
     *
     * @return True iff there was a card to draw
     * @return (conditional) the card
     */
    conditional Card drawCard() {
        if (drawPile.isEmpty()) {
            reshuffle();
        }
        return drawPile.popCard();
    }

    Player nextPlayer(Player current) {
        return current == one ? two : one;
    }

    void displayState() {
        @Inject Console console;
        console.print(GameConstants.DISPLAY_SEPARATOR);
        if (Card top := discardPile.getCard(discardPile.size() - 1)) {
            console.print($"Top of discard: {top}");
        }
        console.print($"{one.name} hand size: {one.hand.size()}");
        console.print($"{two.name} hand size: {two.hand.size()}");
        console.print($"Draw pile size: {drawPile.size()}");
        console.print(PROMPT);
        console.readLine();
    }

    void takeTurn(Player player) {
        @Inject Console console;
        if (Card prev := discardPile.getCard(discardPile.size() - 1)) {
            if (Card card := player.play(this, prev)) {
                console.print($"{player.name} plays {card}");
                discardPile.addCard(card);
            } else {
                console.print($"{player.name} cannot play and passes.");
            }
        }
    }

    /**
     * Play turns until a player wins, and record the win.
     */
    void playGame() {
        @Inject Console console;
        Player current = one;
        while (!isDone()) {
            displayState();
            takeTurn(current);
            current = nextPlayer(current);
        }
        console.print(GameConstants.DISPLAY_SEPARATOR);
        val winner = victor();
        scoreboard.recordWin(winner.name);
        console.print($"{winner.name} wins! ({scoreboard.winsOf(winner.name)} so far)");
    }

    /**
     * What each turn asks of the people playing.
     */
    private static String PROMPT = "Press Enter for next turn...";

    /**
     * @return the player who has played their last card, or else the other one
     */
    private Player victor() {
        return one.hand.isEmpty() ? one : two;
    }
}
