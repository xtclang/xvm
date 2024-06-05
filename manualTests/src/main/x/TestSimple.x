module TestSimple {
    import ecstasy.collections.CaseInsensitive;

    @Inject Console console;

    static function Boolean(String, String) eqInsens = CaseInsensitive.areEqual;

    void run() {
        console.print(eqInsens(hello(), "hello")); // this used to blow up at run-time
    }

    String hello() = "Hello";
}

