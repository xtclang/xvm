/**
 * Tests for the Player class.
 */
module PlayerTest {
    package cardGame import cardGame;

    import cardGame.Card;
    import cardGame.Player;
    import cardGame.Hand;
    import cardGame.Suit;
    import cardGame.Rank;
    import cardGame.GameConstants;

    @Test
    void testPlayerCanBeCreated() {
        Player player = new Player("Alice");
        assert player.name == "Alice" as "Player name should match";
    }

    @Test
    void testPlayerHandIsEmpty() {
        Player player = new Player("Bob");
        assert player.hand.isEmpty() as "New player hand should be empty";
    }

    @Test
    void testPlayerCanHaveCards() {
        Player player = new Player("Charlie");
        Card card = new Card(Suit.Hearts, Rank.Five);
        player.hand.addCard(card);

        assert player.hand.size() == 1 as "Player hand should have 1 card";
    }

    @Test
    void testCardMatches() {
        // Test same suit
        Card card1 = new Card(Suit.Hearts, Rank.Five);
        Card card2 = new Card(Suit.Hearts, Rank.Ten);
        assert Player.cardMatches(card1, card2) as "Cards with same suit should match";
    }

    @Test
    void testCardMatchesSameRank() {
        // Test same rank
        Card card1 = new Card(Suit.Hearts, Rank.Five);
        Card card2 = new Card(Suit.Clubs, Rank.Five);
        assert Player.cardMatches(card1, card2) as "Cards with same rank should match";
    }

    @Test
    void testCardMatchesEight() {
        // Test with eight (wildcard) - eight in first position matches
        Card eight = new Card(Suit.Hearts, GameConstants.SPECIAL_CARD);
        Card otherCard = new Card(Suit.Diamonds, Rank.Five);
        assert Player.cardMatches(eight, otherCard) as "Eight should match any card";
        // Note: Eight is only special in the first position according to cardMatches logic
        // When eight is in second position, it only matches if suit or rank also match
        assert Player.cardMatches(otherCard, new Card(Suit.Hearts, Rank.Five)) as "Cards with same suit should match";
    }

    @Test
    void testCardDoesNotMatch() {
        // Test no match
        Card card1 = new Card(Suit.Hearts, Rank.Two);
        Card card2 = new Card(Suit.Diamonds, Rank.Five);
        assert !Player.cardMatches(card1, card2) as "Card with different suit and rank should not match";
    }

    @Test
    void testPlayerNameStored() {
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");

        assert player1.name == "Player1" as "First player name should be Player1";
        assert player2.name == "Player2" as "Second player name should be Player2";
        assert player1.name != player2.name as "Different players should have different names";
    }

    @Test
    void testPlayerHandExists() {
        Player player = new Player("Alice");
        cardGame.Hand hand = player.hand;
        // The hand should exist and be empty initially
        assert hand.isEmpty() as "Player hand should be empty initially";
    }

    @Test
    void testMultiplePlayers() {
        Player[] players = new Player[];
        players.add(new Player("Alice"));
        players.add(new Player("Bob"));
        players.add(new Player("Charlie"));

        assert players[0].name == "Alice" as "First player should be Alice";
        assert players[1].name == "Bob" as "Second player should be Bob";
        assert players[2].name == "Charlie" as "Third player should be Charlie";
    }

    @Test
    void testPlayerCanAddMultipleCards() {
        Player player = new Player("Test Player");
        Suit[] suits = Suit.values;
        Rank[] ranks = Rank.values;
        Int cardsToAdd = GameConstants.INITIAL_HAND_SIZE;
        Int suitCount = suits.size;
        Int rankCount = ranks.size;
        for (Int i : 0..<cardsToAdd) {
            player.hand.addCard(new Card(suits[i % suitCount], ranks[i % rankCount]));
        }

        assert player.hand.size() == cardsToAdd as "Player hand should have " + cardsToAdd + " cards";
    }
}
