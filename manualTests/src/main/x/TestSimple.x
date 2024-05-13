module TestSimple {

    @Inject Console console;

    void run() {
    }

    void test(Type type) {
        if (type.is(Type<String>) || type.is(Type<Int>)) {
            if (!type.is(Type<String>)) {
                assert type.is(Type<Int>); // this used to compile without a warning
            }
        }
    }
}