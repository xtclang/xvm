module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test t = new Test();
        console.println(t.value1);
        console.println(t.&value1.get()); // this used to blow up
        console.println(t.&value1.checkInside(False));

        Timeout timeout = new Timeout(Duration.MINUTE);
        console.println(timeout.remainingTime);
        console.println(timeout.&remainingTime.get()); // this used to blow up
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
                if (value0 == 0 || !assigned)
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
                    return assigned;
                    }
                else
                    {
                    console.println("inside from outside");
                    return &value0.assigned;
                    }
                }
            }
        }
    }