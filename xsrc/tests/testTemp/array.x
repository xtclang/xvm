module TestArray.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (Array tests)");

        testSimple();
        testStrBuf();
        testConstElement();
        testConstSlice();
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

    void testStrBuf()
        {
        console.println("\n** testStrBuf()");

        StringBuffer sb = new StringBuffer();
        sb.append("hello")
          .append(' ')
          .append("world")
          .append('!');

        console.println("sb=" + sb);
        }

    void testConstElement()
        {
        console.println("\n** testConstElement()");

        String cruel = ["hello", "cruel", "world", "!"] [1];
        console.println("array[1]=" + cruel);
        }

    void testConstSlice()
        {
        console.println("\n** testConstSlice()");

        String[] cruel = ["hello", "cruel", "world", "!"] [1..2];
        console.println("array[1..2]=" + cruel);

        String[] cruel2 = ["hello", "cruel", "world", "!"] [2..1];
        console.println("array[2..1]=" + cruel2);
        }

    void testOOB1()
        {
        Object test = ["hello", "cruel", "world", "!"] [-1];
        }
    void testOOB2()
        {
        Object test = ["hello", "cruel", "world", "!"] [4];
        }
    void testOOB3()
        {
        Object test = ["hello", "cruel", "world", "!"] [1..4];
        }
    void testOOB4()
        {
        Object test = ["hello", "cruel", "world", "!"] [4..1];
        }
    void testOOB5()
        {
        Object test = ["hello", "cruel", "world", "!"] [-1..1];
        }
    void testOOB6()
        {
        Object test = ["hello", "cruel", "world", "!"] [1..-1];
        }
    }