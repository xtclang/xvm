module test_chained {
    typedef <A, B> Tuple<A, B> as Pair<A, B>;
    typedef <A> Pair<A, A> as Twin<A>;

    void run() {
        Twin<Int> t = (1, 2);
    }
}