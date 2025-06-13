module TestSimple {
    @Inject Console console;

    void run( ) {
        String[] strings = ["hello", "world"];
        for (String s : foo(strings)) {
            console.print(s);
        }
    }

    static <Key> Iterator<Key> foo(Key[] keys) {
        return new Iterator<Key>() {
            Iterator<Key> iter = keys.iterator();

            @Override conditional Key next() = iter.next(); // this used to throw at runtime
        };
    }
}
