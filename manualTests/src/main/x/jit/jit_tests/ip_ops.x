/**
 * Tests for the JIT Op classes IP_*.java.
 */
package ip_ops {

    public const TestRunner {
        void run() {
            @Inject Console console;
            console.print(">>>> Running Ip Op Tests >>>>");

            try {
                runTest(() -> new IpAddTests().run());
                runTest(() -> new IpAndTests().run());
                runTest(() -> new IpDivTests().run());
                runTest(() -> new IpModTests().run());
                runTest(() -> new IpMulTests().run());
                runTest(() -> new IpOrTests().run());
                runTest(() -> new IpShlTests().run());
                runTest(() -> new IpShrAllTests().run());
                runTest(() -> new IpShrTests().run());
                runTest(() -> new IpSubTests().run());
                runTest(() -> new IpXorTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Ip Op Tests <<<<<");
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
