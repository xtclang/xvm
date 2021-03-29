module TestSimple.test.org
    {
    @Inject Console console;

    void run(  )
        {
        Point p1 = new Point(1, 1);

        Point p2 = p1.duplicate((x, y) -> (x+1, y+1));

        console.println(p2);
        }

    const Point(Int x, Int y)
        {
        Point duplicate(function (Int, Int)(Int, Int)? transform)
            {
            (Int x1, Int y1) = transform?(x, y) : assert;
            return new Point(x1, y1);
            }
        }
}