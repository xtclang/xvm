module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Boolean successfullyPrepared = False;
        try
            {
            successfullyPrepared = prepare() && seal();
            }
        catch (Exception e)
            {
            console.println($"exception: {e.text}");
            }

        console.println(successfullyPrepared); // used to produce "True"
        }

    Boolean prepare()
        {
        return True;
        }

    Boolean seal()
        {
        assert;
        }
    }
