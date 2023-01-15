module TestSimple
    {
    @Inject Console console;

    void run(String[] args = [])
        {
        Int[] vals = [7, 31, 1, 3, 99];
        assert val range := vals.iterator().range();

        console.println(range); // used to show 3..99
        }
    }