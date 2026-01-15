/**
 * JIT tests for Ecstasy Number classes.
 */
package numbers {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running numbers Tests >>>>");

            Boolean passed = True;
            try {
                passed = runTest(() -> new Int8ConvertTests().run()) && passed;
                passed = runTest(() -> new Int16ConvertTests().run()) && passed;
                passed = runTest(() -> new Int32ConvertTests().run()) && passed;
                passed = runTest(() -> new Int64ConvertTests().run()) && passed;
                passed = runTest(() -> new UInt8ConvertTests().run()) && passed;
                passed = runTest(() -> new UInt16ConvertTests().run()) && passed;
                passed = runTest(() -> new UInt32ConvertTests().run()) && passed;
                passed = runTest(() -> new UInt64ConvertTests().run()) && passed;
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished numbers Tests <<<<<");
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