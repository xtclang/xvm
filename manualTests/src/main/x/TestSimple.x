module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        console.println(new Test(42, "a", True));
        console.println(new @Weight(1) Test(42, "a", True));
        console.println(new @Descr("d") Test(42, "a", True));
        console.println(new @Weight(1) @Descr("d") Test(42, "a", True));
        console.println(new @Descr("d") @Weight(1) Test(42, "a", True));
        }

    const Test(Int f1, String f2, Boolean f3);

    mixin Weight(Int weight) into Const;
    mixin Descr(String descr) into Const;
    }