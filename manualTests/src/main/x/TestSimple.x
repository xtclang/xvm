module TestSimple {
    @Inject Console console;

    package agg import aggregate.xtclang.org;

    import agg.*;

    void run() {
        test([1, 8, 3, 12, 2, 10, 4, 14]);
        test([1, 8, 3, 12, 2, 10, 4, 3]);
        test([1, 8, 3, 12, 2, 10, 4, 14, 3]);
        test([16, 16, 8, 3, 12, 2, 10, 4, 14, 8, 12, 1]);

        void test(Int[] ints) {

            Int[] sorted = ints.sorted();
            console.print(ints);
            console.print(sorted);

            assert Int median := ints.median();
            console.print($"{median=}");

            assert Int mode := sorted.longestRun();
            console.print($"{mode=}");

            Float64[] floats = new Float64[ints.size](i -> ints[i].toFloat64());
            assert Float64 variance := floats.variance();
            Float64 stddev = variance.sqrt();
            console.print($"{variance=} {stddev=}");
        }
    }
}