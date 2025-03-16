module TestSimple {
    @Inject Console console;

    void run() {
        switch (1) { // this used to cause the compiler assert
        case 1:
            console.print("one");
        case 2:
            console.print("two");
            break;
        }
    }
}