module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    conditional Int indexed()
        {
        return False;
        }

    void buildPointer()
        {
        Int? index = Null;
        while (index := indexed()) // this could be replaced with "if", "for", "do-while"
            {
            index++;
            break;
            }

        if (index != Null)         // this used to fail to compile for all those above
            {
            index.maxOf(5);
            }
        }
    }