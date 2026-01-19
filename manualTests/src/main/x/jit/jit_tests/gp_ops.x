/**
 * Tests for the JIT Op classes GP_*.java.
 */
package gp_ops {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running Gp Op Tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new GpAddTests().run());
                passed &= runTest(() -> new GpAndTests().run());
                passed &= runTest(() -> new GpComplTests().run());
                passed &= runTest(() -> new GpDivTests().run());
                passed &= runTest(() -> new GpModTests().run());
                passed &= runTest(() -> new GpMulTests().run());
                passed &= runTest(() -> new GpOrTests().run());
                passed &= runTest(() -> new GpShlTests().run());
                passed &= runTest(() -> new GpShrAllTests().run());
                passed &= runTest(() -> new GpShrTests().run());
                passed &= runTest(() -> new GpSubTests().run());
                passed &= runTest(() -> new GpXorTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Gp Op Tests <<<<<");
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
