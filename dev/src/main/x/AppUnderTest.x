module AppUnderTest {
    void run() {
        @Inject Console console;

        console.print("Should break into the debugger...");
        //assert:debug;
        console.print("Debugging finished.");

        for (Int x : 1..10) {
            console.print("Hello, World!");
        }
        console.print();
    }
}
