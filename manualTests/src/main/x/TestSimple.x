module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        TestB t1 = new TestB();
        t1.report();

        TestD t2 = new TestD();
        t2.report();

        TestConditional<Byte> t3 = new TestConditional(1);
        t3.report();
        }

    class TestB()
            incorporates IncB
        {}

    class TestD()
            extends TestB
            incorporates IncD
        {}

    mixin IncB into TestB
        {
        Int i = 0;

        void report()
            {
            console.println($"reportB {i}");
            }
        }

    mixin IncD
            extends IncB
            into    TestD
        {
        @Override Int i.get()
            {
            return super() + 1;
            }

        @Override
        void report()
            {
            console.println("reportD");
            super();
            }
        }

    class TestConditional<Element>(Element element)
            incorporates conditional IncNumbers<Element extends Number>
            incorporates conditional IncBytes  <Element extends Byte>
        {
        }

    mixin IncNumbers<Element extends Number>
            into TestConditional<Element>
        {
        void report()
            {
            console.println("reportNumbers");
            }
        }

    mixin IncBytes<Element extends Byte>
            extends IncNumbers<Element>
            into TestConditional<Element>
        {
        @Override
        void report()
            {
            console.println("reportBytes");
            super();
            }
        }
    }