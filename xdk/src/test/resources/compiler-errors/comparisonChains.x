module TestComparisonChains {
    // The chain must fold to True, producing a duplicate-case error instead of "constant required".
    void foldsTrue(Boolean value) {
        switch (value) {
        case True: break;
        case 1 < 2 < 3: break;  // expect-error: COMPILER-77 (duplicates True)
        default: break;
        }
    }

}
