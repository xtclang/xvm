module TestSimple {
    @Inject Console console;

    void run() {
        String[] results = new String[];
        String   next    = "012345";

        results += next[1 ..< 4]; // this used to fail to compile
        console.print(results);

    }
}
