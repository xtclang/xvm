/**
 * Tests for the JIT Op classes GP_*.java.
 */
package gp_ops {

    public const TestRunner {
        void run() {
            @Inject Console console;
            console.print(">>>> Running Gp Op Tests >>>>");
            try {
                runTest(() -> new GpAddTests().run());
                runTest(() -> new GpAndTests().run());
                runTest(() -> new GpComplTests().run());
                runTest(() -> new GpDivTests().run());
                runTest(() -> new GpModTests().run());
                runTest(() -> new GpMulTests().run());
                runTest(() -> new GpOrTests().run());
                runTest(() -> new GpShlTests().run());
                runTest(() -> new GpShrAllTests().run());
                runTest(() -> new GpShrTests().run());
                runTest(() -> new GpSubTests().run());
                runTest(() -> new GpXorTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Gp Op Tests <<<<<");
        }

        void runTest(function void () test) {
            try {
                test();
            } catch (IllegalState e) {
                @Inject Console console;
                console.print(e);
            }
        }
    }

}
