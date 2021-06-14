module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        function Boolean(Int) f = compare(Int:-1, 5);
        console.println(f(4));
        }

    static <OtherType extends Number, CompileType extends Orderable>
            function Boolean(CompileType) compare(OtherType unused, CompileType value1)
        {
        return (value2) -> value1 < value2;
        }
    }