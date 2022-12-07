module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m0 = report;
        report(m0);

        Function f0 = report;
        report(f0);

        function Int(Int) f1 = i -> i*i;
        report(f1);

        function void(Object) f2 = report;
        report(f2);

        function void() f3 = &report("hello");
        report(f3);
        }

    void report(Object o)
        {
        console.println($"type  = {&o.actualType}");
        console.println($"value = {o}\n");
        }
    }