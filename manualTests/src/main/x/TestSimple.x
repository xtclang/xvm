module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        new Derived().test(5);
        }

    class Base()
        {
        void test(Int value)
            {
            Child child = new Child(value);
            child.report();

            Child child2 = new Child("hello");
            child2.report();
            }

        class Child(Int value)
            {
            String name = "";
            construct(String s)
                {
                name  = s;
                value = 42;
                }

            void report()
                {
                console.println($"value={value} name={name}");
                }
            }
        }

    class Intermediate
            extends Base
        {
        @Override
        class Child(Int value)
            {
            construct(String s)
                {
                construct Base.Child(s + " there");
                }

            void reportIntermediate()
                {
                }
            }
        }

    class Derived
            extends Intermediate
        {
        @Override
        class Child
            {
            construct(Int value)
                {
                construct Base.Child(-value);
                }

            void reportDerived()
                {
                }
            }
        }
    }