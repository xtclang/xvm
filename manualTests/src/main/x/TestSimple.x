module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        Boolean flag = True;

        Map<Int, String> map;
        if (String name := name(), flag)
            {
            TODO
            }
        else
            {
            map = new HashMap();
            }

        console.println(map); // use to fail to compile: Variable "map" is not definitely assigned
        }

    conditional String name()
        {
        return False;
        }
    }