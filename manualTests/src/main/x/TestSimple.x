module TestSimple {
    @Inject Console console;

    void run() {
        Test t = new Test<String?>();
        console.print($"{t.test()=}");
    }

    class Test<Value> {
        Boolean test() {
            return Null.is(Value); // this used to yield `False`
        }
    }
}