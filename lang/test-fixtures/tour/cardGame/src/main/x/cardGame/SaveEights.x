/**
 * Keeps the wild eights for last: plays any other matching card first, and an eight only when
 * nothing else matches.
 */
const SaveEights
        implements PlayStrategy {

    @Override
    conditional Int choose(Hand hand, Card top) {
        Int? eight = Null;
        for (Int i : 0 ..< hand.size()) {
            if (Card card := hand.getCard(i)) {
                if (card.rank == GameConstants.SPECIAL_CARD) {
                    if (eight == Null) {
                        eight = i;
                    }
                } else if (Player.cardMatches(card, top)) {
                    return True, i;
                }
            }
        }
        return eight.is(Int) ? (True, eight) : False;
    }
}
