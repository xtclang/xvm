module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        TestTurtle<<Int, String>> turtle = new TestTurtle<<Int, String>>();
        console.print($"{turtle.getType(1)=}");

        Tuple<String, Int> tuple = ("a", 1);
        List<Type> types = &tuple.actualType;
        console.print($"{types[1]=}");

        class TestTurtle<TurtleTypes extends Tuple<TurtleTypes>>
            {
            Type getType(Int index)
                {
                List<Type> types = TurtleTypes; // this used to "warn" at run-time
                return types[index];
                }
            }
        }
    }