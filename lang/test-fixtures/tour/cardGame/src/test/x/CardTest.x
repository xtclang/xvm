module CardTest {
    package cardGame import cardGame;

    import cardGame.Card;
    import cardGame.Suit;
    import cardGame.Rank;

    @Test
    void testToStringAceOfHearts() {
        Card card = new Card(Suit.Hearts, Rank.Ace);
        assert card.toString() == "Ace of Hearts" as "Expected 'Ace of Hearts'";
    }

    @Test
    void testToStringKingOfSpades() {
        Card card = new Card(Suit.Spades, Rank.King);
        assert card.toString() == "King of Spades" as "Expected 'King of Spades'";
    }

    @Test
    void testToStringEightOfClubs() {
        Card card = new Card(Suit.Clubs, Rank.Eight);
        assert card.toString() == "8 of Clubs" as "Expected '8 of Clubs'";
    }

    @Test
    void testToStringTwoOfDiamonds() {
        Card card = new Card(Suit.Diamonds, Rank.Two);
        assert card.toString() == "2 of Diamonds" as "Expected '2 of Diamonds'";
    }

    @Test
    void testSuitStoredCorrectly() {
        Card card = new Card(Suit.Clubs, Rank.Five);
        assert card.suit == Suit.Clubs as "Suit should be Clubs";
    }

    @Test
    void testRankStoredCorrectly() {
        Card card = new Card(Suit.Clubs, Rank.Five);
        assert card.rank == Rank.Five as "Rank should be Five";
    }

    @Test
    void testAllSuits() {
        Suit[] suits = Suit.values;
        String[] expectedSuits = ["Hearts", "Diamonds", "Clubs", "Spades"];
        Int suitCount = suits.size;
        assert suitCount == expectedSuits.size as "Suit count should match expected suits";
        for (Int s : 0 ..< suitCount) {
            Card card = new Card(suits[s], Rank.Ace);
            assert card.toString() == $"Ace of {expectedSuits[s]}"
                as $"Expected 'Ace of {expectedSuits[s]}'";
        }
    }

    @Test
    void testAllRanks() {
        Rank[] ranks = Rank.values;
        String[] expectedRanks = ["Ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King"];
        Int rankCount = ranks.size;
        assert rankCount == expectedRanks.size as "Rank count should match expected ranks";
        for (Int r : 0 ..< rankCount) {
            Card card = new Card(Suit.Hearts, ranks[r]);
            assert card.toString() == $"{expectedRanks[r]} of Hearts"
                as $"Expected '{expectedRanks[r]} of Hearts'";
        }
    }

    @Test
    void testTwoCardsWithSameSuitAndRankAreEqual() {
        Card card1 = new Card(Suit.Diamonds, Rank.Seven);
        Card card2 = new Card(Suit.Diamonds, Rank.Seven);
        assert card1.equals(card2) as "Two cards with same suit and rank should be equal";
    }

    @Test
    void testTwoCardsWithDifferentSuitAreNotEqual() {
        Card card1 = new Card(Suit.Hearts, Rank.Seven);
        Card card2 = new Card(Suit.Diamonds, Rank.Seven);
        assert card1 != card2 as "Cards with different suits should not be equal";
    }

    @Test
    void testTwoCardsWithDifferentRankAreNotEqual() {
        Card card1 = new Card(Suit.Hearts, Rank.Seven);
        Card card2 = new Card(Suit.Hearts, Rank.Eight);
        assert card1 != card2 as "Cards with different ranks should not be equal";
    }
}
