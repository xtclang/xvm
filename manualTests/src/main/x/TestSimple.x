module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println("Starting");

        test(7);
        test(Null);
        }

    void test(Int? id)
        {
        if (Int token ?= id, token == id)
            {
            console.println(token);
            }

        if (id != Null)
            {
            // there should be a compiler warning here
            if (Int token ?= id, token == id)
                {
                console.println(token);
                }
            }
        }
    }
