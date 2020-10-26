module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m = TestSimple.foo;
        console.println(m);

        assert m.is(Put);
        console.println($"request={m.request}; path={m.path}");
        }

    mixin Put(String path="")
            extends Http(Put)
        {
        }

    mixin Http(Request request=Get)
            into Method
        {
        enum Request{Get, Post, Put, Delete}

        @Override
        String toString()
            {
            return $"Http{request}";
            }
        }

    @Put("hello")
    void foo()
        {
        }
    }
