module TestSimple {
    @Inject Console console;

    enum Group {A, B, C, D, E, F}

    Int test(Group g) {
        return switch (g)
            {
            case C:    1;
            case B..D: 2; // this used to assert in the compiler
            case A..F: 3;
            };
    }
}
