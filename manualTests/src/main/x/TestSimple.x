module TestSimple {
    @Inject Console console;

    void run() {
        test();
    }

    void test(Int i = 0) {
        switch (1) { // values 1, 2, 7 and 8 used to assert the compiler
        case 1, 2:
            console.print("one or two");
            break;

        case 5:
        case 6:
            console.print("five or six");
            continue;

        case 7:
        case 8:
            console.print("seven or eight");
            break;

        default:
            console.print("whatever");
            break;
        }
    }
}