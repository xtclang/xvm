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
                passed &= runTest(() -> ArrayTests.run());
                passed &= runTest(() -> CallTests.run());
                passed &= runTest(() -> CondMixinTests.run());
                passed &= runTest(() -> EnumTests.run());
                passed &= runTest(() -> GenericTests.run());
                passed &= runTest(() -> LambdaTests.run());
                passed &= runTest(() -> MixinTests.run());
                passed &= runTest(() -> SwitchTests.run());
                passed &= runTest(() -> TryTests.run());
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
