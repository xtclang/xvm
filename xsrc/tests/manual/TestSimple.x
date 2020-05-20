module TestSimple
    {
    @Inject Console console;

    void run(   )
        {
        assert test1(C);
        assert test2(C);
        assert test3(False, C);
        }

    Boolean test1(Group g)
        {
        switch (g)
            {
            case A:
                return False;
            case B..D:
                return True;
            case E..F:
                return False;
            }
        }

    Boolean test2(Group g)
        {
        return switch (g)
            {
            case A:    False;
            case B..D: True;
            case E..F: False;
            };
        }

    Boolean test3(Boolean flag, Group g)
        {
        return switch (flag, g)
            {
            case (True,  A): False;
            case (False, A): False;
            case (_,  B..D): True;
            case (_,  E..F): False;
            };
        }

    enum Group {A, B, C, D, E, F}
    }