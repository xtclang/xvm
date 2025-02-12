module TestSimple {

    import ecstasy.maps.MapCollector;

    @Inject static Console console;

    void run() {
        test1([1="A", 2="B"]);
        test1(new HashMap([1="A", 2="B"]));

        test2([1="A", 2="B"]);
        test2(new HashMap([1="A", 2="B"]));

        test3([1="A", 2="B"]);
        test3(new HashMap([1="A", 2="B"]));
    }

    void test1(Map<Int, String> map) {
        Map<Int, String> map2 = map.map(e -> e.value.toLowercase());
        map2 = map2.reify(); // this used to blow at run-time
        console.print($"++ test1: {map2=}");
    }

    void test2(Map<Int, String> map) {
        assert map.is(Replicable);
        function Map<Int, String>() replicate = map.&new();
        Map<Int, String> mapR = replicate();
        console.print($"++ test2: {mapR=}");
    }

    void test3(Map<Int, String> map) {
        assert map.is(Duplicable);
        function Map<Int, String>() duplicate = map.&new(map);
        Map<Int, String> mapD = duplicate();
        console.print($"++ test3: {mapD=}");
    }
}