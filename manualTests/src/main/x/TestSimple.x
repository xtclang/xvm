module TestSimple {
    @Inject Console console;

    void run() {
        val t = ("s", 1);
        Int x = t[1]; // this used to fail to compile (IntLiteral conversion)
        console.print($"{x=} {&x.actualType=}");
    }
}
