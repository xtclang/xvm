module TestSimple {
    @Inject Console console;

    void run() {
        function conditional String(Int) f1 = i -> {
            return False; // this used to fail to compile
        };
        function conditional String(Int) f2 = i -> {
            return i >= 0 ? (True, "positive") : False; // this used to fail to compile
        };

        if (String s := f1(0)) {
            console.print($"f1()={s}");
        } else {
            console.print($"f1()=[No result]");
        }

        if (String s := f2(0)) {
            console.print($"f2()={s}");
        } else {
            console.print($"f2()=[No result]");
        }

        Map<Int, String> map = [1="a", 2="b"];
        function conditional String(Int) f3 = map.get;

        if (String s := f3(1)) {
            console.print($"f3()={s}");
        } else {
            console.print($"f3()=[No result]");
        }
    }
}