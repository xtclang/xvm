module Test1Tuple {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("\n** testMutability()");

        Tuple<Int, String, Char> t1 = (1, "big", '?');
        console.print($"{t1} - {t1.mutability}");

        Tuple t1a = Tuple:().add(Int:1).add("big").add('?');
        assert t1a == t1;

        Tuple<Int, String, Char> t2 = t1.ensureMutability(Fixed);
        t2[1] = "small";
        console.print($"{t2} - {t2.mutability}");

        Tuple<String, Char> t3 = t2[1..2];
        console.print($"{t3}  - {t3.mutability}");

        Tuple t4 = t2.slice(1..2); // "small", ?
        assert t4 == t3;

        Tuple t5 = Tuple:(1.toInt()).addAll(t4); // 1, "small", ?
        assert t5 == t2;
    }
}
