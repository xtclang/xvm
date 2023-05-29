module tck.xtclang.org {

    /**
     * This is temporary, for manual testing only; will be replaced by the xUint framework.
     */
    void run() {
//        constructors.Basic b = new constructors.Basic();
//        b.testUnFreezable();
//
//        constructors.Medium m = new constructors.Medium();
//        m.testFinalizerChain();
        new array.Basic().run();
        new array.Medium().run();
        new comparison.Compare().run();
        new comparison.Hash().run();
        new operations.Basic().run();
        new numbers.Decimals().run();
    }
}