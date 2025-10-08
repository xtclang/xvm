module test4.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;

    void run() {
        testSwitch1(0);
        testSwitch1(4);
        testSwitch1(171);
    }

    void testSwitch1(Int i) {
        switch (i) {
        case 0:
            console.print("a");
            break;

        case 1-6:
            console.print("b");
            break;

        case 7:
            console.print("c");
            break;

        case 14,15:
            console.print("d");
            break;

        default:
            console.print("other");
            break;
        }
    }
}