class Deck extends CardCollection {
    construct(String label) {
        construct CardCollection(label);
    } finally {
        for (Suit suit : Suit.values) {
            for (Rank rank : Rank.values) {
                addCard(new Card(suit, rank));
            }
        }
    }
}
