module TestSimple {
    @Inject Console console;

    void run() {
        console.print($"{++count}");
        console.print($"{count}"); // this used to print "0"
    }

    @Transient Int count = 0;
}

