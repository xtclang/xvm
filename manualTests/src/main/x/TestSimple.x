module TestSimple {
    @Inject Console console;

    void run() {
        String s = "hello    there";
        console.print(s.split(' '));       // old behavior
        console.print(s.split(' ', True)); // new behavior
    }
}
