module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    const Test<Key, Value>
        {
        void foo(function Boolean match(Map<Key, Value>.Entry), HashMap<Key, Value> map)
            {
            Loop: for ((Key k, Value v) : map)
                {
                if (match(Loop.entry))
                    {
                    console.println(k);
                    }
                }
            }
        }
    }
