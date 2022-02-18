module TestSimple
    {
    @Inject Console console;

    void run()
        {
        for (String s : ["abc=123&xyz=789", "abc,abc=123,xyz", "abc", "abc="])
            {
            var map = s.splitMap(entrySeparator='&');
            console.println($"string={s}, map={map}");
            console.println($"map[abc]={map["abc"]}, map[def]={map["def"]}, map[xyz]={map["xyz"]}");
            console.println();
            }
        }
    }
