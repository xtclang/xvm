module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    const Test
        {
        String name;

        @Override
        String toString()
            {
            return $"{name.quoted()}";
            return $"{name.quoted()}"; // this used to blow the compiler
            }
        }
    }