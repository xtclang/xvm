module TestSimple
    {
    @Inject Console console;

    Point p;
    void run()
        {
        console.print($"{p=}");
        console.print($"{Point.default=}");
        }

    const Point(Int x, Int y)
            default(new Point(0, 0));
    }