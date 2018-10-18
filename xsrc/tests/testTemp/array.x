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

        String blind = (3, "blind", "mice", "!") [1];
        console.println("tuple(1)=" + blind);

        Int num = (3, "blind", "mice", "!") [0];
        console.println("tuple(0)=" + num);
        }

    void testConstSlice()
        {
        console.println("\n** testConstSlice()");

        String[] cruel = ["hello", "cruel", "world", "!"] [1..2];
        console.println("array[1..2]=" + cruel);

//        String[] cruel2 = ["hello", "cruel", "world", "!"] [2..1];
//        console.println("array[2..1]=" + cruel2);
//
//        Tuple<Int, String> blind = (3, "blind", "mice", "!") [0..1];
//        console.println("tuple[0..1]=" + blind);
//
//        Tuple<String, Int> blind2 = (3, "blind", "mice", "!") [1..0];
//        console.println("tuple[1..0]=" + blind2);
        }
    }