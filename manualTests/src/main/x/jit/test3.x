module test3.examples.org {

    import ecstasy.io.IOException;

    void run() {
        @Inject Console console;

        TRY:
        try {
            testThrow(1);
        } catch (IOException e) {
            console.print("1) IOException caught");
        } catch (Unsupported e) {
            console.print("2) Unsupported caught 1");
            throw e;
        } finally {
            console.print("1) Finally: ", True);
            console.print(TRY.exception);
        }

        for (Int i : 0..2) {
            try {
                testThrow(i);
            } catch (IOException e) {
                console.print("2) IOException caught");
                continue;
            } catch (Unsupported e) {
                console.print("2) Unsupported caught 2");
                // break;
//            } finally {
//                console.print("Done");
            }
        }

        console.print("Done normally");
    }

    void testThrow(Int i) {
        if (i == 0) {
            throw new IOException("Test IO");
        } else if (i == 1) {
            throw new Unsupported("Test Unsupported");
        } else {
            throw new IllegalState("Test IllegalState");
        }
    }
}