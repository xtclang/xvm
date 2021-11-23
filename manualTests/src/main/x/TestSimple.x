module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        recover();
        }

    void recover()
        {
        for (Int i : 0..2)
            {
            Int store = storeFor(i);

            try
                {
                if (deepScan())
                    {
                    continue;
                    }
                }
            catch (Exception e)
                {
                console.println("MUST NOT THROW"); // this used to log for i==1
                }
            }
        }

    Int storeFor(Int i)
        {
        if (i == 0)
            {
            return i;
            }
        TODO
        }

    Boolean deepScan()
        {
        return True;
        }
    }
