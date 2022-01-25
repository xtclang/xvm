module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    package collections import collections.xtclang.org;
    import collections.*;

    void run()
        {
        Derived d = new Derived(5, "hell0");
        d.foo(1);
        }

    void report(Object o)
        {
        // console.println($"{&o.actualType}: {o}");
        }

    class Base(Int value)
        {
        void foo(Int a1, Int a2=0)
            {
            Int a3;
            console.println($"{a1} {a2}");
            // console.println($"{a1} {a2} {&a3.assigned}"); // blows up
            }
        }

    const Derived()
            extends Base
        {
        String name;
        construct(Int value, String name)
            {
            this.name = name;
            super(value);
            }

        @Override
        void foo(Int a1, Int a2=2, Int a3=3)
            {
            super(a1);
            super(a1, a2);
            //super(a1, a2, a3);  // this should not compile; deferred
            }
        }
    }
