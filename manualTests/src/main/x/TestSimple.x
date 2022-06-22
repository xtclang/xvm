module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import ecstasy.io.*;
    import json.*;

    void run()
        {
        Schema schema = Schema.DEFAULT;

        Map<Int, String> map = new HashMap();
        map = map.put(1, "a");
        map = map.put(2, "b");
        testSer(schema, "map", new Test(map));

//        HashMap<String, String> map = new HashMap();
//        map = map.put(1, "a");
//        map = map.put(2, "b");
//        testSer(schema, "map", new Test(map));
        }
    }