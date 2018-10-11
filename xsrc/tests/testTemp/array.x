module TestArray.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (Array tests)");

        testSimple();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Int[] nums = [1,7];
        console.println("array=" + nums);
        console.println("size=" + nums.size);
        console.println("[0]=" + nums[0]);
        console.println("[1]=" + nums[1]);
        }
    }