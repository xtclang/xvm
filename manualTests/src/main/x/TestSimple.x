module TestSimple
    {
    @Inject Console console;

    void run() {
        Test t = new Test();
        console.print(t.test(["acc", "b"]));
    }

    class Test {
        Int test(String[] names) {
            names = names.filter(n -> n.startsWith("a"));
            return names.map(n -> n.size)
                        .reduce(0, (n1, n2) -> n1 + n2); // used to fail to compile
        }
    }
}