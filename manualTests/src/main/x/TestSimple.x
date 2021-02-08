module TestSimple.test.org
    {
    @Inject Console console;
    void run()
        {
        C c = new C();
        c.foo();

        String? s = Null;
        StringBuffer sb = new StringBuffer();
        sb.append(s?).append("shit");
        console.println(sb);
        }

    class C
        {
        construct()
            {
            n = -1;
            }

        // TODO private
        public Int n.get()
            {
            Int i = super();
            return i;
            }

        void foo()
            {
            console.println(n);
            n = 5;
            console.println(n);
            }
        }
    }