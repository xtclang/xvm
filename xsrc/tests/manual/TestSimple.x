module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Label: for (Int i : 1..3)
            {
            console.println($"i={i} first={Label.first} last={Label.last} count={Label.count}");
            }

        Label: for (Int i : 4..6)
            {
            console.println($"i={i} first={Label.first} last={Label.last} count={Label.count}");
            }
        }
    }