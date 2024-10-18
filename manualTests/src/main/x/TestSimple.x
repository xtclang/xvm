module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print(new Test().value);
    }

    class Test {
        @Lazy Int value.get() = calculateValue(); // this used to throw "Unknown outer" exception

        Int calculateValue() = 42;
    }
}