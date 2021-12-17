module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int[] elements = [];

        assert:test
            {
            if (elements.size <= 1)
                {
                return False;
                }

            val iter = elements.iterator();
            assert iter.next();
            return True;
            } as $"elements are not provided in order: {elements}";

        console.println("all good");
        }
    }