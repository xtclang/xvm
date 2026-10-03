/**
 * Tests for the JIT Enums.
 */
package enums {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running Enum Tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new ColorTests().run());
                passed &= runTest(() -> new BooleanTests().run());
                passed &= runTest(() -> new MutabilityTests().run());
                passed &= runTest(() -> new NullableTests().run());
                passed &= runTest(() -> new OrderedTests().run());
                passed &= runTest(() -> new RoundingTests().run());
                passed &= runTest(() -> new SignumTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Enum Tests <<<<<");
            return passed;
        }

        Boolean runTest(function void () test) {
            try {
                test();
                return True;
            } catch (IllegalState e) {
                @Inject Console console;
                console.print(e);
            }
            return False;
        }
    }
}
