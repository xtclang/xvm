module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        String? s = Null;
        Int     i = 0;

        try
            {
            if ((s != Null) || (i == 0))
                {
                i = s.size;
                }
            }
        catch (Exception e)
            {
            console.println("Compiler failure 1");
            }

        try
            {
            if ((s == Null) && (i == 1))
                {
                }
            else
                {
                i = s.size;
                }
            }
        catch (Exception e)
            {
            console.println("Compiler failure 2");
            }
        }
    }