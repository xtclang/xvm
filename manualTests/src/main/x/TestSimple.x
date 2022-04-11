module TestSimple
    {
    @Inject Console console;

    package oodb import oodb.xtclang.org;

    void run()
        {
        import oodb.Connection;

        Test test = new Test()
            {
            @Override
            void foo()
                {
                Type t1 = String;
                Type t2 = Connection.parameterize([t1]); // this used to assert during compilation
                console.println(t2);
                }
            };
        }

    interface Test
        {
        void foo();
        }
    }