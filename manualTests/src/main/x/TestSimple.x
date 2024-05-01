module TestSimple {
    @Inject Console console;

    void run() {
        // for this to work, run the command line:
        //      xec -I hello="Hi there"
        @Inject String hello;

        console.print(hello);
    }
}