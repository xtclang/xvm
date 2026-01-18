/**
 * Basic JIT tests
 */
package basic {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running basic tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> arrayTests.run());
                passed &= runTest(() -> callTests.run());
                passed &= runTest(() -> condMixinTests.run());
                passed &= runTest(() -> enumTests.run());
                passed &= runTest(() -> genericTests.run());
                passed &= runTest(() -> lambdaTests.run());
                passed &= runTest(() -> mixinTests.run());
                passed &= runTest(() -> switchTests.run());
                passed &= runTest(() -> tryTests.run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished basic tests <<<<<");
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
