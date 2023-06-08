module TestSimple
    {
    @Inject Console console;

    void run() {
        immutable Int[] array = [1,2,3];
        immutable Int[] bigger = array + 4;    // used to fail to compile
        immutable Int[] sliced = bigger[2..3]; // used to fail to compile

        console.print($"{array=}, {&array.actualType=}");
        console.print($"{bigger=}, {&bigger.actualType=}");
        console.print($"{sliced=}, {&sliced.actualType=}");
    }
}