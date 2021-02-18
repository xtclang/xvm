module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Type t = Map<String, Int>;

        assert Type k := t.resolveFormalType("Key");
        console.println(k);
        }
    }