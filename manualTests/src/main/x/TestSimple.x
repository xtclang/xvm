module TestSimple {

    @Inject Console console;

    void run() {
        String? s = "hello";
        console.print(s);
        s.makeImmutable();
        s = Null;
        console.print(s);
        s.makeImmutable(); // used to fail
    }
}