module TestSimple.test.org
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    void run()
        {
        Int    n = 3;
        Byte   b = n.toByte();
        log.add($"n={n}, b={b}");

        function Byte(Int) convert = Number.converterFor(Int, Byte);
        log.add($"convert(3)={convert(3)}");
        log.add($"convert(45)={convert(45)}");
        }
    }