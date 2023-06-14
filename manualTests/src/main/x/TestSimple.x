module TestSimple {
    @Inject Console console;

    void run() {
    }

    import ecstasy.collections.Aggregator;

    class Test<Element>(Element element)
        delegates Collection<Element>(reify()) { // used to produce a confusing error

        Set<Element> reify() {
            TODO
        }
    }
}