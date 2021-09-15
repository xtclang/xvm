module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        MyArray<String> a = new MyArray();
        a.reify();
        }

    interface MyList<Element>
        {
        MyList reify()
            {
            return this;
            }
        }

    class MyArray<Element>
            implements Delegate<Element>
            implements MyList<Element>
        {
        private static interface Delegate<Element>
            {
            Delegate reify(Int mutability = 0)
                {
                return this;
                }
            }

        @Override
        MyArray reify(Int mutability = 0)
            {
            assert as "we must see this exception";
            }
        }
    }