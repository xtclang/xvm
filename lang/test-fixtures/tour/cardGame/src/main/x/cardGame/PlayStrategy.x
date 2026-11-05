/**
 * How a player chooses which of the cards in their hand to play on the top of the discard pile.
 */
interface PlayStrategy {
    /**
     * Choose a card to play.
     *
     * @param hand  the cards the player holds
     * @param top   the card on the top of the discard pile
     *
     * @return True iff a card in the hand can be played on `top`
     * @return (conditional) the index of that card in the hand
     */
    conditional Int choose(Hand hand, Card top);
}
