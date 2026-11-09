/**
 * File system JIT tests
 */
package files {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running file system tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new FileStoreTests().run());
                passed &= runTest(() -> new FileNodeTests().run());
                passed &= runTest(() -> new DirectoryTests().run());
                passed &= runTest(() -> new FileTests().run());
                passed &= runTest(() -> new FileChannelTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished file system tests <<<<<");
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
