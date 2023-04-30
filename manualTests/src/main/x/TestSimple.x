module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test svc = new Test();
        }

    interface Iface
        {
        Int foo();
        }

    service Test
        {
        Int foo()
            {
            return super(); // used to report "non virtual super"
            }
        }
    }