/**
 * Switch JIT tests
 */
package switch_ {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running switch tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> int8SwitchTests.run());
                passed &= runTest(() -> int16SwitchTests.run());
                passed &= runTest(() -> int32SwitchTests.run());
                passed &= runTest(() -> int64SwitchTests.run());
                passed &= runTest(() -> int128SwitchTests.run());
                passed &= runTest(() -> uint8SwitchTests.run());
                passed &= runTest(() -> uint16SwitchTests.run());
                passed &= runTest(() -> uint32SwitchTests.run());
                passed &= runTest(() -> uint64SwitchTests.run());
                passed &= runTest(() -> uint128SwitchTests.run());
                passed &= runTest(() -> stringSwitchTests.run());
                passed &= runTest(() -> enumSwitchTests.run());
                passed &= runTest(() -> typeSwitchTests.run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished switch tests <<<<<");
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
