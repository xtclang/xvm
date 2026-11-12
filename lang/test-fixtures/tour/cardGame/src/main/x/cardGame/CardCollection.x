/**
 * An ordered pile of cards with a label, such as a hand, the draw pile or a deck. The last card is
 * the top of the pile.
 */
class CardCollection(String label) {
    private Card[] cards = new Card[];

    void addCard(Card card) {
        cards.add(card);
    }

    /**
     * Take the top card off the pile.
     *
     * @return True iff the pile had a card
     * @return (conditional) the card
     */
    conditional Card popCard() {
        if (cards.empty) {
            return False;
        }
        Card card = cards[cards.size - 1];
        cards.delete(cards.size - 1);
        return True, card;
    }

    conditional Card removeCard(Int index) {
        if (index < cards.size) {
            Card card = cards[index];
            cards.delete(index);
            return True, card;
        }
        return False;
    }

    Boolean isEmpty() = cards.empty;

    Int size() = cards.size;

    conditional Card getCard(Int index) {
        return index < cards.size
                ? (True, cards[index])
                : False;
    }

    void clear() {
        cards.clear();
    }

    void swapCards(Int index1, Int index2) {
        if (index1 < cards.size && index2 < cards.size) {
              cards.swap(index1, index2);
        }
    }

    /**
     * Put the cards in a random order.
     */
    void shuffle() {
        @Inject Random rnd;
        for (Int i : cards.size >..0 ) {
            Int j = rnd.int(i + 1);
            swapCards(i, j);
        }
    }

    /**
     * Move cards from the top of this pile to another, until it has moved `numCards` or this pile
     * is empty.
     */
    void deal(CardCollection! that, Int numCards) {
        for (Int i : 0..<numCards) {
            if (Card card := popCard()) {
                that.addCard(card);
            } else {
                return;
            }
        }
    }

    void dealAll(CardCollection! that) {
        deal(that, size());
    }
}
