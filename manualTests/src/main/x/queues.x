module TestQueues
    {
    package collections import collections.xtclang.org;

    import collections.ArrayDeque;
    import collections.CircularArray;

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