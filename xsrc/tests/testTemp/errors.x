module TestCompilerErrors.xqiz.it
    {
    // arrays
    void testAOOB1()
        {
        Object test = ["hello", "cruel", "world", "!"] [-1];
        }
    void testAOOB2()
        {
        Object test = ["hello", "cruel", "world", "!"] [4];
        }
    void testAOOB3()
        {
        Object test = ["hello", "cruel", "world", "!"] [1..4];
        }
    void testAOOB4()
        {
        Object test = ["hello", "cruel", "world", "!"] [4..1];
        }
    void testAOOB5()
        {
        Object test = ["hello", "cruel", "world", "!"] [-1..1];
        }
    void testAOOB6()
        {
        Object test = ["hello", "cruel", "world", "!"] [1..-1];
        }

    // tuples
    void testTOOB1()
        {
        Tuple test = (3, "blind", "mice", "!") [-1..1];
        }
    void testTOOB2()
        {
        Tuple test = (3, "blind", "mice", "!") [1..4];
        }
    void testTOOB3()
        {
        Tuple test = (3, "blind", "mice", "!") [4..1];
        }
    void testTOOB4()
        {
        Tuple test = (3, "blind", "mice", "!") [1..-1];
        }
    void testTOOB5()
        {
        Object test = (3, "blind", "mice", "!") [-1];
        }
    void testTOOB6()
        {
        Object test = (3, "blind", "mice", "!") [4];
        }

    // asserts
    void testAssert()
        {
        console.println("\n** testAssert()");
        assert;
        console.println("(done)");
        }

    void testAssertFalse()
        {
        console.println("\n** testAssertFalse()");
        assert false;
        console.println("(done)");
        }
    }