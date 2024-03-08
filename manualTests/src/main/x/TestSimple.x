module TestSimple {
    @Inject Console console;

    void run() {
        Class c = ListMap<Int, Int>;
        assert c.incorporates(ecstasy.collections.ListMapIndex); // this used to NPE
    }
}
