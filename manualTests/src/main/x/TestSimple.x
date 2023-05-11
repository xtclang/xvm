module TestSimple
    {
    @Inject Console console;

    package oodb import oodb.xtclang.org;

    void run()
        {
        console.print();
        }

    interface AuthSchema
            extends oodb.DBSchema
        {
        @RO
        // @oodb.Initial("")                  // this used to compile
        // @oodb.Initial(Point.OriginString)  // this used to compile
        @oodb.Initial(Point.Origin)
        oodb.DBValue<Point> config;
        }

    const Point(Int x, Int y)
        {
        static Point Origin = new Point(0, 0);
        static String OriginString = "0,0";
        }
    }