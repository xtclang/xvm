module test_mixin {
    typedef <A, B> Tuple<A, B> as Pair<A, B>;
    typedef <A> Pair<A, A> as Twin<A>;

    mixin Xor into Twin<Int> {
        Int xor() {
            return this[0] ^ this[1];
        }
    }

    void run() {
        Twin<Int> t = (5, 3);
        Int result = t.xor();
    }
}