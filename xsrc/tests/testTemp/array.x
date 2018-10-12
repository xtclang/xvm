module TestArray.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (Array tests)");

        testSimple();
        testStrBuf();
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
    }