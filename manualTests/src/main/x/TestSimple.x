module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method m = TestSimple.foo;
        console.println(m);

        assert m.is(Put);
        console.println($"request={m.request}; path={m.path}");

        Property<TestSimple, Int> p = TestSimple.count;
        console.println(p);

        assert p.is(Put);
        console.println($"request={p.request}; path={p.path}");
        }

    mixin Put(String path="")
            extends Http(Put)
        {
        }

    mixin Http(Request request=Get)
            into Method | Property
        {
        enum Request{Get, Post, Put, Delete}

        @Override
        String toString()
            {
            return $"Http{request} {this.is(Property) ? "property" : "method"}";
            }
        }

    @Put("hello")
    void foo()
        {
        }

    @Put("goodbye")
    Int count.get()
        {
        return 0;
        }
    }
