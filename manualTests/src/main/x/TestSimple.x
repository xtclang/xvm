module TestSimple {

    @Inject Console console;

    void run() {
        floats();
        doubles();
    }

    void floats() {
        Float64 f1 = Int32:123;
        Float   f2 = Int:123;
        Float   f3 = 123;

        console.print($"${f1=}");
        console.print($"${f2=}");
        console.print($"${f3=}");
    }

    void doubles() {
        Double d1 = 123;
        Double d2 = 123.0;
        Double d3 = Double:123.0;

        console.print($"${d1=}");
        console.print($"${d2=}");
        console.print($"${d3=}");
    }
}