module TestSimple
    {
    @Inject Console console;

    void run()
        {
        C<Int> c = new C(1);

        console.println(c.format(5));
        }

    interface Iface<T extends Int>
        {
        String format(T t);
        }

    mixin M<T extends Int>
            into Iface<T>
        {
        construct()
            {
            prefix = T.toString() + ": ";
            }

        String prefix;

        @Override
        String format(T t)
            {
            return prefix + t.toString();
            }
        }

    class C<T>
            incorporates conditional M<T extends Int>
        {
        construct(T t)
            {
            base = t;
            }
        T base;
        }
    }
