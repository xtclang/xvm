module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test();
        console.println(t.value1);
        }

    class Test
        {
        Int value0;

        Boolean checkOutside()
            {
            console.println("outside");
            return True;
            }

        @Stub
        Int value1
            {
            @Override
            Int get()
                {
                if (value0 == 0)
                    {
                    console.println("set 1");
                    set(1); // this used to blow up
                    }

                if (checkOutside())
                    {
                    console.println("set 2");
                    set(2);
                    }

                if (checkInside())
                    {
                    console.println("set 3");
                    set(3);
                    }

                return super();
                }

            Boolean checkInside()
                {
                console.println("inside");
                return checkSuper(); // this used to blow up at run-time
                }
            }
        }

    mixin Stub into Var
        {
        protected Boolean checkSuper()
            {
            console.println("super");
            return True;
            }
        }
    }