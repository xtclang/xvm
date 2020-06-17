module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        @Inject Console console;

        import stuff.*;
        console.println($"point={new Point(1,2)}");

        Point.ping();
        Point.pong();

        import stuff.Point.ping;
        import stuff.Point.pong;

        ping();
        pong();
        }

    package stuff
        {
        const Point(Int x, Int y)
            {
            static void ping()
                {
                @Inject Console console;
                console.println("ping!");
                }

            static function void() pong = ping;
            }
        }
    }
