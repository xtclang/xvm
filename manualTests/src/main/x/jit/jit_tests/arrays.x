/**
 * Arrays JIT tests
 */
package arrays {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running arrays tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> arrayTests.run());
                passed &= runTest(() -> charArrayTests.run());
                passed &= runTest(() -> int8ArrayTests.run());
                passed &= runTest(() -> int16ArrayTests.run());
                passed &= runTest(() -> int32ArrayTests.run());
                passed &= runTest(() -> int64ArrayTests.run());
                passed &= runTest(() -> int128ArrayTests.run());
                passed &= runTest(() -> uint8ArrayTests.run());
                passed &= runTest(() -> uint16ArrayTests.run());
                passed &= runTest(() -> uint32ArrayTests.run());
                passed &= runTest(() -> uint64ArrayTests.run());
                passed &= runTest(() -> uint128ArrayTests.run());
                passed &= runTest(() -> dec32ArrayTests.run());
                passed &= runTest(() -> dec64ArrayTests.run());
                passed &= runTest(() -> dec128ArrayTests.run());
                passed &= runTest(() -> float32ArrayTests.run());
                passed &= runTest(() -> float64ArrayTests.run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished array tests <<<<<");
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
