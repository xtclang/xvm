module TestQueues
    {
    import ecstasy.collections.ArrayDeque;
    import ecstasy.collections.CircularArray;
    import ecstasy.collections.Queue;

    @Inject ecstasy.io.Console console;

    void run()
        {
        testSimple();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        ArrayDeque<String> deque = new ArrayDeque();
        deque.add("hello");
        deque.add("world");
        while (String s := deque.next())
            {
            console.println("next()=" + s);
            }
        }
    }