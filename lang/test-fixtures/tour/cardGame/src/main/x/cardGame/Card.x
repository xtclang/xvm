/**
 * A playing card: a suit and a rank, such as the Queen of Hearts, one of a standard deck's 52:
 * https://en.wikipedia.org/wiki/Standard_52-card_deck.
 */
const Card(Suit suit, Rank rank) {
    @Override
    String toString() {
        return $"{rank} of {suit}";
    }
}
