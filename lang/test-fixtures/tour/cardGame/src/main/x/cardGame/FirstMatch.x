/**
 * Plays the first card in the hand that matches the top of the discard pile.
 */
const FirstMatch
        implements PlayStrategy {

    @Override
    conditional Int choose(Hand hand, Card top) {
        for (Int i : 0 ..< hand.size()) {
            if (Card card := hand.getCard(i)) {
                if (Player.cardMatches(card, top)) {
                    return True, i;
                }
            }
        }
        return False;
    }
}
