module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println($"TestSimple.is(immutable}={TestSimple.is(immutable)}");
        console.println($"TestSimple.is(const}={TestSimple.is(const)}");
        console.println($"TestSimple.is(package}={TestSimple.is(package)}");
        console.println($"TestSimple.is(module}={TestSimple.is(module)}");
        console.println($"TestSimple.is(service}={TestSimple.is(service)}");

        assert console.is(service | immutable);
        assert !console.is(class);
        }
    }
