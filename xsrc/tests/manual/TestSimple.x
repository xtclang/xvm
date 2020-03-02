module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        }

    function Int[] (Map.Entry<String, Int[]>) TAKE = e ->
        {
        Int[] result = e.value;
        e.remove();
        return result;
        };
    }
