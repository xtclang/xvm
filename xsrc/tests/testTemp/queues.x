module TestQueues.xqiz.it
    {
    import Ecstasy.collections.ArrayDeque;
    import Ecstasy.collections.CircularArray;
    import Ecstasy.collections.Queue;

    @Inject X.io.Console console;

    void run()
        {
//        Int n = 1;
//        Bit[] bits = n.to<Bit[]>();
//        for (Bit bit : bits)
//            {
//            console.print(""+bit);
//            }
//        console.println("");

        testSimple();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        ArrayDeque<String> deque = new ArrayDeque();
        deque.add("hello");
        deque.add("world");
        while (String s : deque.next())
            {
            console.println("next()=" + s);
            }
        }
    }