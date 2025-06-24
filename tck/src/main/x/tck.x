module tck.xtclang.org {

    /**
     * This is temporary, for manual testing only; will be replaced by the xUint framework.
     */
    void run() {
        new clazz.Basic().run();
        new array.Basic().run();
        new comparison.Compare().run();
        new comparison.Hash().run();
        new cond.Basic().run();
        new elvis.Basic().run();
        new loops.Basic().run();
        new numbers.Decimals().run();
        new operations.Basic().run();
        new tuples.Basic().run();
        new tuples.MultiReturn().run();
        new time.Basic().run();
        new constructors.Basic().run();
        new union.Basic().run();
        new services.Basic().run();
        new array.Medium().run();
        // Currently commented out typevars
        new clazz.Medium().run();
        //new comparison.Medium().run();
//        new constructors.Medium().run();
//        new constructors.Reflect().run();
//        new inner.Basic().run();
    }
}