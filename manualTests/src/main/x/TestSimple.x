module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        }

    typedef (String | String[]) as QueryParameter;

    QueryParameter test(QueryParameter prevValue, String value)
        {
        if (prevValue.is(String))
            {
            return [prevValue, value]; // this used to fail to compile
            }
        return prevValue + value;
        }
    }