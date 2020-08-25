module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Starting");

        Int n = 0;

        Base b = new Base(1);
        console.println(b);

        Base b2 = b.new(b);
        console.println(b2);

        Base d = new Derived(2);
        console.println(d);

        Base d2 = d.new(d);
        console.println(d2);

        Empty e = d2;
        }

    class Base(Int i)
            implements ecstasy.Duplicable
        {
        construct(Int i)
            {
            console.println($"constructing {&this.actualType} {i}");
            }
        construct(Base b)
            {
            console.println($"copy {&this.actualType} {b}");
            this.i = b.i + 1;
            }
        }

    class Derived(Int i)
            extends Base
        {
        construct(Int i)
            {
            construct Base(i);
            }

        // comment the constructor below to test the compiler error
        construct(Derived d)
            {
            construct Base(d);
            }
        }

    interface Empty
        {
        construct();
        }
    }

