/**
 * Tests for the Deck class.
 */
module DeckTest {
    package cardGame import cardGame;

    import cardGame.Card;
    import cardGame.Deck;
    import cardGame.Suit;
    import cardGame.Rank;
    import cardGame.GameConstants;

    @Test
    void testDeckHas52Cards() {
        Deck deck = new Deck("Standard Deck");
        assert deck.size() == GameConstants.DECK_SIZE as "Deck should have " + GameConstants.DECK_SIZE + " cards";
    }

    @Test
    void testDeckCanPopCard() {
        Deck deck = new Deck("Standard Deck");
        if (Card card := deck.popCard()) {
            assert deck.size() == GameConstants.DECK_SIZE - 1 as "Deck should have " + (GameConstants.DECK_SIZE - 1) + " cards after pop";
        } else {
            assert False as "Pop should succeed on full deck";
        }
    }

    @Test
    void testDeckCanBeShuffled() {
        Deck deck = new Deck("Standard Deck");
        deck.shuffle();
        assert deck.size() == GameConstants.DECK_SIZE as "Deck should still have " + GameConstants.DECK_SIZE + " cards after shuffle";
    }

    @Test
    void testDeckCanDealCards() {
        Deck deck = new Deck("Standard Deck");
        cardGame.Hand hand = new cardGame.Hand("Player Hand");

        deck.deal(hand, GameConstants.INITIAL_HAND_SIZE);
        assert deck.size() == GameConstants.DECK_SIZE - GameConstants.INITIAL_HAND_SIZE as "Deck should have " + (GameConstants.DECK_SIZE - GameConstants.INITIAL_HAND_SIZE) + " cards after dealing " + GameConstants.INITIAL_HAND_SIZE;
        assert hand.size() == GameConstants.INITIAL_HAND_SIZE as "Hand should have " + GameConstants.INITIAL_HAND_SIZE + " cards";
    }

    @Test
    void testDeckCanClear() {
        Deck deck = new Deck("Standard Deck");
        deck.clear();
        assert deck.isEmpty() as "Deck should be empty after clear";
    }

    @Test
    void testDeckHasAllSuits() {
        Deck deck = new Deck("Standard Deck");
        Int suitCount = Suit.values.size;
        Boolean[] suitFound = new Boolean[suitCount];
        for (Int i : 0..<suitCount) {
            suitFound[i] = False;
        }

        for (Int i : 0..<GameConstants.DECK_SIZE) {
            if (Card card := deck.getCard(i)) {
                suitFound[card.suit.ordinal] = True;
            }
        }

        Boolean allFound = True;
        for (Int i : 0..<suitCount) {
            allFound = allFound && suitFound[i];
        }
        assert allFound as "Deck should have cards from all " + suitCount + " suits";
    }

    @Test
    void testDeckHasAllRanks() {
        Deck deck = new Deck("Standard Deck");
        Int rankCount = Rank.values.size;
        Boolean[] rankFound = new Boolean[rankCount];
        for (Int i : 0..<rankCount) {
            rankFound[i] = False;
        }

        for (Int i : 0..<GameConstants.DECK_SIZE) {
            if (Card card := deck.getCard(i)) {
                rankFound[card.rank.ordinal] = True;
            }
        }

        Boolean allFound = True;
        for (Int i : 0..<rankCount) {
            allFound = allFound && rankFound[i];
        }
        assert allFound as "Deck should have cards from all " + rankCount + " ranks";
    }

    @Test
    void testDeckPopAllCards() {
        Deck deck = new Deck("Standard Deck");
        Int count = 0;

        while (Card card := deck.popCard()) {
            count++;
        }

        assert count == GameConstants.DECK_SIZE as "Should be able to pop all " + GameConstants.DECK_SIZE + " cards";
        assert deck.isEmpty() as "Deck should be empty";
    }
}
