/**
 * A player of the game: a name, a hand of cards, and a strategy for choosing what to play.
 */
class Player {
    public String name;
    public Hand   hand;

    /**
     * How the player chooses which matching card to play.
     */
    public PlayStrategy strategy = new FirstMatch();

    construct(String name) {
        this.name = name;
        this.hand = new Hand(name + " Hand");
    }

    /**
     * Play a card on the previous one: a matching card from the hand if there is one, or else
     * cards drawn from the draw pile until one matches.
     *
     * @param eights  the game, whose draw pile the player draws from
     * @param prev    the card on the top of the discard pile, if any
     *
     * @return True iff the player plays a card
     * @return (conditional) the card played
     */
    conditional Card play(Eights eights, Card? prev) {
        if (Card card := searchForMatch(prev)) {
            return True, card;
        }
        return drawForMatch(eights, prev);
    }

    /**
     * @return True iff the hand has a card that can be played on `prev`, as the strategy chooses
     * @return (conditional) that card, taken from the hand
     */
    conditional Card searchForMatch(Card? prev) {
        if (prev == Null) {
            return hand.popCard();
        }
        if (Int index := strategy.choose(hand, prev)) {
            return hand.removeCard(index);
        }
        return False;
    }

    /**
     * Draw cards until one can be played on `prev`; the others go into the hand.
     *
     * @return True iff a card that matches was drawn
     * @return (conditional) that card
     */
    conditional Card drawForMatch(Eights eights, Card? prev) {
        @Inject Console console;
        while (True) {
            if (Card card := eights.drawCard()) {
                console.print($"{name} draws {card}");
                if (prev == Null || cardMatches(card, prev)) {
                    return True, card;
                }
                hand.addCard(card);
            } else {
                return False;
            }
        }
    }

    /**
     * @return True iff `card1` can be played on `card2`: the same suit or rank, or a wild eight
     */
    static Boolean cardMatches(Card card1, Card card2) {
        return card1.suit == card2.suit
            || card1.rank == card2.rank
            || card1.rank == GameConstants.SPECIAL_CARD;
    }
}
