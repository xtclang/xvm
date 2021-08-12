module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int i1;
        Int i2;
        if (i1 := test(1), Int i3 := test(3), i2 := test(2), Int i4 := test(4))
            {
            console.println(i1 + i2 + i3 + i4);
            }
        else
            {
            console.println("failed");
            return;
            }
        console.println(i1 + i2);
        }

    conditional Int test(Int i)
        {
        return True, i;
        }
    }
