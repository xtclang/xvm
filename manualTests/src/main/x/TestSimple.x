module TestSimple {
    @Inject Console console;

    void run() {
        Ex ex = new Ex(1, "hi");
        console.print($"{ex=}");

        Ex ex2 = ex.new(2, "bye"); // this used to fail at runtime
        console.print($"{ex2=}");
    }

    interface CI {
        construct(Int n, String s);
    }

    const Ex(Int n, String s) implements CI {}
}