class Hand extends CardCollection {
    construct(String label) {
        construct CardCollection(label);
    }

    void display() {
        @Inject Console console;
        console.print($"{label}:");
        for (Int i : 0 ..< size()) {
            if (Card card := getCard(i)) {
                console.print(card);
            }
        }
        console.print();
    }
}
