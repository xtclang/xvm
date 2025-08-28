module TestSimple {
    @Inject Console console;

    void run() {
        Int[] ints = new Int[];
        ints += 17;

        assert ints.mutability == Mutable;
        assert !ints.is(Const); // this used to throw
    }

    interface DBMap<Key extends immutable Const, Value extends immutable Const> {}

    // this used to be allowed to compile without "immutable" modifier
    mixin FileChunkIds into DBMap<String, immutable Int[]> {}
}
