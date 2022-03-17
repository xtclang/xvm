module TestSimple
    {
    @Inject Console console;

    package collections import collections.xtclang.org;

    import ecstasy.collections.*;
    import collections.*;

    void run()
        {
        Map<String, Int> map = Map:["A"=0, "B"=1, "C"=3, "D"=4];

        console.println(map);

        StringBuffer buf = new StringBuffer(map.estimateStringLength(sep="; ", keySep="->"));
        console.println(map.appendTo(buf, pre="Map: ", post=Null, sep="; ", keySep="->"));
        }
    }