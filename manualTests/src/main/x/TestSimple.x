module TestSimple {
    @Inject Console console;

    void run() {
        test1();
    }

    void test1(Boolean flag = True) {
        import ecstasy.collections.CaseInsensitive;

        if (flag) {
            Type<String>.Orderer ord = CaseInsensitive.compare(_,_); // this used to fail to compiler\

            console.print($"{ord("a", "b")}");
            console.print($"{ord("b", "a")}");
        }
    }
}