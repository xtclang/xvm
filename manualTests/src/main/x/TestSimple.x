module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Boolean[] doors = new Boolean[100];
        for (Int pass : 0 ..< 100)
            {
            for (Int door = pass; door < 100; door += 1+pass)
                {
                doors[door] = !doors[door];
                }
            }

        // this used to fail to compile
        console.println($"open doors: {doors.mapIndexed((d, i) -> d ? i+1 : 0).filter(i -> i > 0)}");
        }
    }