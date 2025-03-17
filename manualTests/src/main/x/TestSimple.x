module TestSimple {
    @Inject Console console;

    void run() {
        // this used to assert during BAST production
        switch (1) {
        case 1:
            console.print("one");
            continue;
        case 2:
            console.print("two");
            break;
        default:
            console.print("whatever");
            break;
        }
    }
}