module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print(testUnreachable(Int:5));
        }

    String testUnreachable(Object o)
        {
        return switch (o.is(_))
            {
            case IntNumber, FPNumber: "Number";
            case Int:    "Int"; // TODO: should be an error: unreachable
            case String, Char: "String";
            default:     "other";
            };
        }

    String testUnreachableType(Type t)
        {
        switch (t.is(_))
            {
            case Type<IntNumber>: return "Number";
            case Type<Int>: return "Int";          // TODO: should be an error: unreachable
            // case Char, String: return "chars";  // this used to compile: Type<Char> required
            default:  return "other";
            }
        }
    }