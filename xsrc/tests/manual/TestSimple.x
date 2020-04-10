module TestSimple.xqiz.it
    {
    @Inject Console console;

    const Metadata<N extends Number>(String name);

    void run()
        {
        Metadata<Int> m = new Metadata("foo");
        Type t = m.N;

        switch (t)
            {
            case Int:
                console.println("Int case");
                break;
            case Dec:
                console.println("Dec case");
                break;
            default:
                throw new IllegalArgument($"Unsupported type {t}");
            }

        assert Class c := t.fromClass();
        switch (c)
            {
            case Int:
                console.println("Int case");
                break;
            case Dec:
                console.println("Dec case");
                break;
            default:
                throw new IllegalArgument($"Unsupported class {c}");
            }

        console.println("\n** done");
        }

    <Resource> Resource getResource(Type<Resource> type, String name)
        {
        Boolean wrongName = False;

        switch (Resource)
            {
            case Console:
                if (name == "console")
                    {
                    return console.as(Resource);
                    }
                wrongName = True;
                break;

            case Clock:
                if (name == "clock")
                    {
                    return clock.as(Resource);
                    }
                wrongName = True;
                break;
            }

        throw wrongName
                ? new Exception($"Invalid resource type: {type}")
                : new Exception($"Invalid resource name: {name}");
        }
    }