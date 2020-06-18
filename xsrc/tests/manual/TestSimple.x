module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println();

        Boolean flag1 = True;
        Boolean flag2 = !flag1;
        Int?    opt   = 42;

        if (Int i := f(1), Int j := f(i))
            {
            console.println(j);
            }

        if (flag1 && flag2, Int j := f(1))
            {
            console.println(j);
            }

        if (flag1, Int j ?= opt)
            {
            console.println(j);
            }

        if (flag1, opt != Null)
            {
            console.println(opt + 1);
            }
        }

     conditional Int f(Int i)
        {
        return True, i+1;
        }
    }
