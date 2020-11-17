module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Boolean flag = True;
        Byte[]? bytes = Null;

        bytes = Null;
        if (flag)
            {
            bytes = [0];
            }
        flag = fromBytes(bytes?);

        bytes = Null;
        if (flag)
            {
            }
        else
            {
            bytes = [1];
            }

        flag = fromBytes(bytes?);

        bytes = Null;
        try
            {
            bytes = [2];
            }
        catch (Exception e)
            {
            }

        bytes = Null;
        try
            {
            bytes = [3];
            }
        finally
            {
            flag = False;
            }

        flag = fromBytes(bytes?);
        }

    protected Boolean fromBytes(Byte[] bytes)
        {
        return True;
        }

    void foo()
        {
        }
    }
