module TestSimple {
    @Inject Console console;

    void run() {
        Test t = new Test();
//        console.print(t.getFunction1()());
        console.print(t.getFunction2()("hello"));
    }

    class Test {
//        function Int() getFunction1() {
//            return foo;  // that used to allow to compile and would blow at runtime
//        }

        function Int(String) getFunction2() {
            return foo;  // that used to fail to compile
        }

        Int foo(String s, Int i=42) {
            console.print($"{s=}");
            return ++i;
        }
    }
}