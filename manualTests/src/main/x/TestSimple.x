module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int x = 4;
        Int y = 5;
        console.print($"{x=}, {y=}, {foo(x,y)=}");
        }

    Int foo(Int x, Int y) = x+y;
    }
