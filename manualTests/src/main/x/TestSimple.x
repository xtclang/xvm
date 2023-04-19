module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print(test(2));
        }

    String test(Int i)
        {
        loop:
        for (Int j : 0..i)
            {
            switch (i)
                {
                default:
                    return "other";
                case 0:
                    return "zero";
                case 1:
                    return "small";
                case 2:
                    if (j > 0)
                        {
                        continue; // used to be allowed
                        }
                    else
                        {
                        continue loop;
                        }
                }
            }
        return "weird";
        }
    }