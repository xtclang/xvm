module tck.xtclang.org {

    /**
     * This is temporary, for manual testing only; will be replaced by the xUint framework.
     */
    void run() {
//        constructors.Basic b = new constructors.Basic();
//        b.testUnFreezable();

//        constructors.Medium m = new constructors.Medium();
//        m.testAssertChain();

//        constructors.Reflect r = new constructors.Reflect();
//        r.testAssertChain();

//        tuple.Basic t = new tuple.Basic();
//        t.testSlice();

//        inner.Basic i = new inner.Basic();
//        i.testCallChain();
//        m.testFinalizerChain();
        new array.Basic().run();
        new array.Medium().run();
        new comparison.Compare().run();
        new comparison.Hash().run();
        new operations.Basic().run();
        new numbers.Decimals().run();
    }
}