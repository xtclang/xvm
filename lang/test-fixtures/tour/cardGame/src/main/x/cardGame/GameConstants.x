/**
 * Game configuration constants for the Eights card game.
 */
class GameConstants {
    /**
     * Number of cards dealt to each player at the start of the game.
     */
    static Int INITIAL_HAND_SIZE = 5;

    /**
     * Total number of cards in a standard deck.
     */
    static Int DECK_SIZE = Rank.values.size * Suit.values.size;

    /**
     * The card rank that acts as a wild card (can be played on any card).
     */
    static Rank SPECIAL_CARD = Eight;

    /**
     * Minimum number of cards that must remain in the discard pile
     * before reshuffling the draw pile.
     */
    static Int MIN_DISCARD_PILE_FOR_RESHUFFLE = 1;

    /**
     * Visual separator line used for display formatting.
     */
    static String DISPLAY_SEPARATOR = "=============================";

    /**
     * Default name for the draw pile.
     */
    static String DRAW_PILE_NAME = "Draw Pile";

    /**
     * Default name for the discard pile.
     */
    static String DISCARD_PILE_NAME = "Discard Pile";

    /**
     * Default deck name.
     */
    static String DECK_NAME = "Deck";
}
