module TestSimple.xqiz.it
    {
    @Inject Console console;

    const Metadata<N extends Number>(String name);

    void run()
        {
        Metadata<Int> m = new Metadata("foo");
        Type t = m.N;

        console.println($"Type = {t}");

        switch (t)
            {
            case Int:
                console.println("Int case");
                break;
            case Dec:
                console.println("Dec case");
                break;
            default:
                throw new IllegalArgument($"Unsupported type {m.N}");
            }

        console.println("\n** done");
        }
    }