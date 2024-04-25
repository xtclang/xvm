module StringComparisons {
    void run() {
        @Inject Console console;
        import ecstasy.collections.CaseInsensitive;

        String[] tests = ["dog", "cat", "Dog"];
        String s1 = tests[0];
        for (String s2 : tests) {
            // Comparing two strings for exact equality
            if (s1 == s2) {
                console.print($"{s1} == {s2}");
            }

            // Comparing two strings for inequality
            if (s1 != s2) {
                console.print($"{s1} != {s2}");
            }

            // Comparing two strings to see if one is lexically ordered
            // before the other
            if (s1 < s2) {
                console.print($"{s1} < {s2}");
            }

            // Comparing two strings to see if one is lexically ordered
            // after the other
            if (s1 > s2) {
                console.print($"{s1} > {s2}");
            }

            // How to achieve both case sensitive comparisons and case
            // insensitive comparisons within the language

            // TODO GG #1 comment out the import at the top and uncomment this import:
            // import ecstasy.collections.CaseInsensitive;

            // TODO GG #2 java.lang.AssertionError at org.xvm.asm.constants.MethodConstant.getValueType(MethodConstant.java:478)
            // Type<String>.Orderer ord = CaseInsensitive.compare(_,_);

            // TODO GG #3
            // Type<String>.Comparer cmp = CaseInsensitive.areEqual;
            // Type<String>.Orderer  ord = CaseInsensitive.compare;

            // TODO GG #4 (here "String" is the Type<String>
            // String.Comparer cmp = CaseInsensitive.areEqual;
            // String.Orderer  ord = CaseInsensitive.compare;

            if (CaseInsensitive.areEqual(s1, s2)) {
                console.print($"{s1} == {s2} (case-insensitive)");
            } else {
                console.print($"{s1} != {s2} (case-insensitive)");
            }

            switch (CaseInsensitive.compare(s1, s2)) {
            case Lesser:
                console.print($"{s1} < {s2} (case-insensitive)");
                break;
            case Equal:
                // already covered this one above
                assert CaseInsensitive.areEqual(s1, s2);
                break;
            case Greater:
                console.print($"{s1} > {s2} (case-insensitive)");
                break;
            }
        }
    }
}
