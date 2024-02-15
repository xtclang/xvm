module TestSimple {
    @Inject Console console;

    void run() {
        testInt();
        testBool();
    }

    void testInt() {
        Int[] a = [0];
        assert a.is(immutable);
        try {
          Int x = ++a[0]; // used to allow the change
          assert;
        } catch (ReadOnly expected) {}
    }

    void testBool() {
        Boolean[] a = [False];
        assert a.is(immutable);
        try {
          Boolean x = ++a[0];  // used to assert in the native layer
          assert;
        } catch (ReadOnly expected) {}
    }
}

