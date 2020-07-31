module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        TestProperty t = new TestProperty();
        console.println(t.value);
        t.value = 6;
        console.println(t.value);
        }

    // this is a code example that didn't compile prior to the fix

    interface List2<Element>
            extends Collection2<Element>
        {
        @Future Int future;

        Int value
            {
            Int base = 1;
            @Override
            Int get()
                {
                return base;
                }

            @Override
            void set(Int i)
                {
                if (assigned)
                    {
                    base = i + 1;
                    }

                super(i);
                }
            }
        }

    interface Collection2<Element>
        {
        typedef function Ordered (Element, Element) Orderer;

        @RO Boolean distinct;
        }
    }

