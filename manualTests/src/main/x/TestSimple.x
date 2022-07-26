module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TimeOfDay t = TimeOfDay:23:09:12.99999999999999999999999;
        console.println(t);

        console.println(TimeOfDay:23:59:60.toString());

        console.println(TimeOfDay:23:59:59.123-TimeOfDay:23:59:59.456);
        console.println(TimeOfDay:23:59:60.123-TimeOfDay:23:59:60.456);
        console.println(TimeOfDay:23:59:60.123-TimeOfDay:23:59:59.456);
        console.println(TimeOfDay:23:59:59.123-TimeOfDay:23:59:60.456);

        console.println(TimeOfDay:23:59:59.123+Duration:1s);
        console.println(TimeOfDay:23:59:60.123+Duration:0.1s);
        console.println(TimeOfDay:23:59:60.123+Duration:1s);
        }
    }