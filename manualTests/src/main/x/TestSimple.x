module TestSimple {
    @Inject Console console;

    void run( ) {
        String[] strings = [];
        String[] other = ["a", "b"];
        val v = other.filter(s -> s.size == 1); // this used to fail to compile
        strings.addAll(v);
    }
}