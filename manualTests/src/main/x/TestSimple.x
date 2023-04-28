module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test svc = new Test();

        console.print(svc.test0().foo());
        console.print(svc.test1().foo());
        console.print(svc.test2().foo());
        }

    interface Iface
        {
        Int foo();
        }

    service Test
        {
        class Child
                implements Iface
            {
            @Override
            Int foo()
                {
                return 0;
                }
            }

        Iface test0()
            {
            return new Child();
            }

        Iface test1()
            {
            return new Child1(); // this used to fail at run-time

            class Child1
                    implements Iface
                {
                @Override
                Int foo()
                    {
                    return 1;
                    }
                }
            }

        Iface test2()
            {
            Iface iface = new Iface() // this used to fail at run-time
                {
                @Override
                Int foo()
                    {
                    return 2;
                    }
                };
            return iface;
            }
        }
    }