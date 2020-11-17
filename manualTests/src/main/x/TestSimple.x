module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    protected void transition(function Boolean(Int)? canTransition = Null)
        {
        Int glance = 5;

        assert canTransition?(glance);
        }
    }
