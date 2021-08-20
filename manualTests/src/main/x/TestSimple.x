module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        report(   1..5   );
        report(  [1..5)  );
        report(  (1..5)  );
        }

    void report(Range<Int> range)
        {
        String s = "012345678";
        console.println(s.slice(range));
        }
    }
