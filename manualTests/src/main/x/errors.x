module TestCompilerErrors
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
    void testConstruct()
        {
        Int[] array = new Int[7, (i) -> -1];
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

    // methods
    class TestMethods
        {
        static void testMethod1()
            {
            function void () m1 = testMethod2;      // no "this"
            function void () m2 = Test.testMethod2; // no target
            }

        void testMethod2() {}
        }
    }