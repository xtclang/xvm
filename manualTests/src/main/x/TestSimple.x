module TestSimple {
    @Inject Console console;

    import ecstasy.SharedContext;

    void run() {
        using(sharedStringOne.withValue("stringOne")) {
            using(new Timeout(Duration:0.01S)) { // small timeout used to cause RT error
                using(sharedStringTwo.withValue("stringTwo")) {
                    console.print(sharedStringOne.hasValue().as(Tuple));
                    console.print(sharedStringTwo.hasValue().as(Tuple));
                }
            }
        }
    }

    static SharedContext<String> sharedStringOne = new SharedContext("sharedStringOne");
    static SharedContext<String> sharedStringTwo = new SharedContext("sharedStringTwo");
}