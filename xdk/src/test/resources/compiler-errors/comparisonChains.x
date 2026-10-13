module TestComparisonChains {
    // Each constant chain must fold to the Boolean case above it. A duplicate-case error
    // proves both that folding happened and that it produced the right value.
    void foldsTrue(Boolean value) {
        switch (value) {
        case True: break;
        case 1 < 2 < 3: break;  // expect-error: COMPILER-77 (duplicates True)
        case 1 < 2 <= 2: break;  // expect-error: COMPILER-77 (duplicates True)
        case 3 > 2 > 1: break;  // expect-error: COMPILER-77 (duplicates True)
        case 3 >= 3 > 1: break;  // expect-error: COMPILER-77 (duplicates True)
        case 1 == 1 == 1: break;  // expect-error: COMPILER-77 (duplicates True)
        case 1 != 2 != 3: break;  // expect-error: COMPILER-77 (duplicates True)
        case 'a' < 'b' <= 'c': break;  // expect-error: COMPILER-77 (duplicates True)
        default: break;
        }
    }

    void foldsFalse(Boolean value) {
        switch (value) {
        case False: break;
        case 1 < 0 < 2: break;  // expect-error: COMPILER-77 (duplicates False)
        case 1 < 2 < 0: break;  // expect-error: COMPILER-77 (duplicates False)
        case 1 == 1 == 2: break;  // expect-error: COMPILER-77 (duplicates False)
        case 1 == 2 == 2: break;  // expect-error: COMPILER-77 (duplicates False)
        case 1 != 1 != 2: break;  // expect-error: COMPILER-77 (duplicates False)
        case 1 != 2 != 2: break;  // expect-error: COMPILER-77 (duplicates False)
        case 'z' < 'b' < 'c': break;  // expect-error: COMPILER-77 (duplicates False)
        default: break;
        }
    }

    // A runtime operand prevents folding, even after a constant prefix decides the result.
    // Such a chain must still be rejected where a constant case value is required.
    void retainsRuntimeAfterTruePrefix(Boolean value) {
        switch (value) {
        case 1 < 2 < runtimeValue(): break;  // expect-error: COMPILER-78 (runtime operand)
        default: break;
        }
    }

    void retainsRuntimeAfterFalsePrefix(Boolean value) {
        switch (value) {
        case 2 < 1 < runtimeValue(): break;  // expect-error: COMPILER-78 (runtime operand)
        default: break;
        }
    }

    static Int runtimeValue() = 3;
}
