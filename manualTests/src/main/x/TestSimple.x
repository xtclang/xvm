module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("*** single");
        for (Int i : 0..3)
            {
            switch (foo(i).is(_))
                {
                case String:
                    console.println($"String {i}");
                    break;
                case Object:
                    console.println($"Object {i}");
                    break;
                }
            }

        console.println("*** multi");
        for (Int i : 0..3)
            {
            switch (foo(2).is(_), i)
                {
                case (String, 1):
                    console.println($"String {i}");
                    break;
                case (IntNumber, 1..2):
                    console.println($"Number in range {i}");
                    break;
                case (IntNumber, _):
                    console.println($"Number out of range {i}");
                    break;
                default:
                    console.println($"Object {i}");
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
            };
        }
    }