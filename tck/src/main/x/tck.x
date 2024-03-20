module tck.xtclang.org {

    /**
     * This is temporary, for manual testing only; will be replaced by the xUint framework.
     */
    void run() {
        new array.Basic().run();
        new array.Medium().run();
        new comparison.Compare().run();
        new comparison.Hash().run();
        new operations.Basic().run();
        new numbers.Decimals().run();
        new tuples.Basic().run();
        new services.Basic().run();
        new constructors.Basic().run();
        new comparison.Medium().run();
        new constructors.Medium().run();
        new constructors.Reflect().run();
        new inner.Basic().run();
    }
}