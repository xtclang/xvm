module TestSimple {
    @Inject Console console;

    void run() {
    }

    class B(String value) {
        String value {
            @Override
            String get() {
                return value; // clearly wrong; it should use "super()" instead of self-referencing,
                              // but this used to produce a non-decipherable error
            }

            @Override
            void set(String newValue, Boolean update = False) {
                super(newValue);
            }
        }
    }
}