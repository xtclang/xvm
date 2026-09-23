/**
 * JIT tests for Ecstasy temporal classes.
 */
package temporal {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running temporal Tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new DateTests().run());
                passed &= runTest(() -> new DurationTests().run());

            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished temporal Tests <<<<<");
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
