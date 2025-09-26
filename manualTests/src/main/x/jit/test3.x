module test3.examples.org {

    import ecstasy.io.IOException;

    void run() {
        @Inject Console console;

        for (Int i : 0..2) {
            try {
                testThrow(i);
            } catch (IOException e) {
                console.print("IOException caught");
                continue;
            } catch (Unsupported e) {
                console.print("Unsupported caught");
                break;
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