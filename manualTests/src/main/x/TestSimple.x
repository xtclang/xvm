module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Test();
        }

    class Test
        {
        construct(Map<String, String[]> userRoles = [])
            {
            Int s = userRoles.size;
            for ((String user, String[] roles) : userRoles) // this used to blow up at run-time
                {
                console.print(user);
                }
            }
        }
    }