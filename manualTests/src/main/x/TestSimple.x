module TestSimple {
    @Inject Console console;

    void run() {
        test1();
        test2();
    }

    void test1() {
        import ecstasy.collections.CaseInsensitive;

        Type<String>.Orderer ord = CaseInsensitive.compare(_,_); // this used to assert in the compiler

        console.print($"{ord("a", "b")}");
        console.print($"{ord("b", "a")}");
    }

    void test2() {
        import ecstasy.collections.CaseInsensitive;

        Type<String>.Comparer cmp = CaseInsensitive.areEqual; // that used to produce a compiler error

        console.print($"{cmp("a", "b")}");
        console.print($"{cmp("a", "a")}");
    }
}