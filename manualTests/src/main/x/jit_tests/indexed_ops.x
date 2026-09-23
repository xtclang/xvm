/**
 * Tests for the JIT Op classes that work with non-array indexed types (e.g. types that implement
 * UniformIndexed).
 */
package indexed_ops {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running Indexed Op Tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new ElementAccessorTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Indexed Op Tests <<<<<");
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
