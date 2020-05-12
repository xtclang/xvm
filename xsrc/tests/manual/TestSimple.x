module TestSimple
    {
    @Inject Console console;

    void run()
        {
        report(TestSimple);
        report(ecstasy);
        report(utilities.misc);
        }

    void report(Class m)
        {
        console.println(m);
        console.println($"name={m.name}");
        }

    package utilities
        {
        package misc
            {
            }
        }
    }