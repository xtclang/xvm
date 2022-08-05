module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        Test t = new Test();
        console.println(t.value1);
        console.println(t.&value1.checkInside(False));
        }

    class Test
        {
        Int value0;

        Boolean checkOutside()
            {
            console.println("outside");
            return True;
            }

        Int value1
            {
            @Override
            Int get()
                {
                if (value0 == 0)
                    {
                    console.println("set 1");
                    set(1);
                    }

                if (checkOutside())
                    {
                    console.println("set 2");
                    set(2);
                    }

                if (checkInside(True))
                    {
                    console.println("set 3");
                    set(3);
                    }

                return super();
                }

            Boolean checkInside(Boolean fromInside)
                {
                if (fromInside)
                    {
                    console.println("inside from inside");
                    return True;
                    }
                else
                    {
                    console.println("inside from outside");
                    return &value0.assigned; // this used to blow up
                    }
                }
            }
        }
    }