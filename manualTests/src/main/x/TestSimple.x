module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Method method = &foo;
        Tuple  result;

        result = method.invoke(this, Tuple:());
        console.println(result[0]);  // that used to fail to compile
        }

    String foo()
        {
        return "hi";
        }
    }
