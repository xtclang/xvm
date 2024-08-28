module TestSimple {
    @Inject Console console;

    void run() {
        new FixedRealm(""); // this used to assert in the compiler
    }

    interface Realm {
        @RO Boolean readOnly.get() = False;
    }

    const FixedRealm(String name) implements Realm {
        <T> conditional T readOnly() {
            return False;
        }
    }
}
