/**
 * A playing card: a suit and a rank, such as the Queen of Hearts.
 */
const Card(Suit suit, Rank rank) {
    @Override
    String toString() {
        return $"{rank} of {suit}";
    }
}
