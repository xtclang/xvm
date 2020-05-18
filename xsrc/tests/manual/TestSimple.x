module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        assert test(C);
        }

    Boolean test(Group g)
        {
        switch (g)
            {
            case A:
                return False;

            case D..B:
                return True;

            case F:
                return False;
            }

        TODO
        }

    enum Group {A, B, C, D, E, F}
    }