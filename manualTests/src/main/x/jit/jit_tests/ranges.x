/**
 * Range JIT tests
 */
package ranges {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running ranges tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> int64RangeTests.run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished ranges tests <<<<<");
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
