module TestSimple {

    @Inject Console console;

    void run() {
        Map<Int, String> m = [1="a", 2="b"];

        console.print(m.toString(pre="", post="", sep =",\n"));
    }
}