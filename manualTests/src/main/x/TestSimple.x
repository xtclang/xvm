module TestSimple {
    @Inject Console console;

    void run() {
        String s = "hello    there";
        console.print(s.split(' ', False));  // old behavior
        console.print(s.split(' '));         // new behavior
    }
}
