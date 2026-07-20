package basicSwitchTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running basic switch tests >>>>");

        testSwitchOnProperties();

        console.print(">>>> Finished basic switch tests >>>>");
    }

    void testSwitchOnProperties() {
        Test t1 = new Test(0, 0);
        Test t2 = new Test(1, 1);
        Test t3 = new Test(0, 1);
        assert t1.switchOnProperties() == 1;
        assert t2.switchOnProperties() == 2;
        assert t3.switchOnProperties() == 3;
    }

    const Test(Int n1, Int n2) {
        Int switchOnProperties() {
            return switch (n1, n2) {
                case (0, 0): 1;
                case (1, 1): 2;
                default:     3;
            };
        }
    }
}