module TestSimple {

    @Inject Console console;

    void run() {
        while (True) {
            String s = console.readLine("Test>");
            console.print($"+++ {s} ---");
        }
    }
}