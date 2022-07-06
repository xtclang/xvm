module TestSimple
    {
    @Inject Console console;

    void run()
        {
        for (Int i : 0..3)
            {
            switch (foo(i).is(_))
                {
                case String:
                    console.println("String");
                    break;
                case Object:
                    console.println("Object");
                    break;
                }
            }
        }

    Object foo(Int i)
        {
        return switch (i)
            {
            case 0: Int:0..Int:3;
            case 1:  "hello";
            default: Int:3;
            } ;
        }
    }