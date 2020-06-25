module Example1
    {
    @Inject Console console;

    void run()
        {
        console.println("Hello world!");

        @Inject ecstasy.Timer timer;
        timer.reset();

        @Inject FileStore storage;
        console.println($"root={storage.root} capacity={storage.capacity}");

        @Inject Clock clock;
        console.println(clock.now);

        console.println($"Elapsed {timer.elapsed.milliseconds} ms");
        }
    }
