module TestSimple
    {
    Boolean echo = True;
    @Inject(opts=echo) Console console; // used to assert in the compiler

    void run()
        {
         // both lines used to fail at run time
        console.print(oodb);
        console.print(oodb.isInvalidName("hello"));
        }
    }