module TestSimple.test.org
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    void run()
        {
        report(Int32);
        report(String);
        report(Boolean);
        report(Point);
        }

    void report(Type t)
        {
        if (val c := t.fromClass(), val d := c.defaultValue())
            {
            log.add($"default value for {t.DataType} is {d}");
            }
        else
            {
            log.add($"no default value for {t.DataType}");
            }
        }

    const Point(Int x, Int y)
            default(Origin)
        {
        static Point Origin = new Point(0, 0);
        }
    }