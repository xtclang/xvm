module TestSimple
    {
    @Inject Console console;

    void run(   )
        {
        Byte b = 255;
        Int n = b.toUnchecked().toInt8().toInt();
        console.println(n);
        }
    }