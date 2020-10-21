module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Parent().test();
        }

    class Base()
        {
        }

    class Parent()
            extends Base
        {
        void test()
            {
            new Child().test();

            Base   b = this.Base;
            Parent p = this.Parent;
            console.println(b);
            console.println(p);
            }

        class Child
            {
            void test()
                {
                Base   b = this.Base;
                Parent p = this.Parent;
                console.println(b);
                console.println(p);
                }
            }
        }
    }
