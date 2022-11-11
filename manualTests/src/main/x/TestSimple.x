module TestSimple
    {
    @Inject Console console;
    void run()
        {
        Object[] valArray = [];
        Object value = "test";
        valArray += value;  // this used to call addAll(Iterable<Char>) instead pf add(Object)
        console.println(valArray);
        }
    }