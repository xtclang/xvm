module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestTurtle<<Int, String>> turtle = new TestTurtle<<Int, String>>();
        console.print($"{turtle.getType(1)=}");

        Tuple<String, Int> tuple = ("a", 1);
        UniformIndexed<Int, Type> types = &tuple.actualType;
        console.print($"{types[1]=}");

        class TestTurtle<TurtleTypes extends Tuple<TurtleTypes>>
            {
            Type getType(Int index)
                {
                UniformIndexed<Int, Type> types = TurtleTypes;
                return types[index];
                }
            }
        }
    }