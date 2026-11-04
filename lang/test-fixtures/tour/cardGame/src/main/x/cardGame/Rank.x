enum Rank(String display) {
    Ace("Ace"),
    Two("2"),
    Three("3"),
    Four("4"),
    Five("5"),
    Six("6"),
    Seven("7"),
    Eight("8"),
    Nine("9"),
    Ten("10"),
    Jack("Jack"),
    Queen("Queen"),
    King("King");

    @Override
    String toString() {
        return display;
    }
}
