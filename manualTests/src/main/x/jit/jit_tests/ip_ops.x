/**
 * Tests for the JIT Op classes IP_*.java.
 */
package ip_ops {

    public const TestRunner {
        Boolean run() {
            @Inject Console console;
            console.print(">>>> Running Ip Op Tests >>>>");

            Boolean passed = True;
            try {
                passed &= runTest(() -> new IpAddTests().run());
                passed &= runTest(() -> new IpAndTests().run());
                passed &= runTest(() -> new IpDivTests().run());
                passed &= runTest(() -> new IpModTests().run());
                passed &= runTest(() -> new IpMulTests().run());
                passed &= runTest(() -> new IpOrTests().run());
                passed &= runTest(() -> new IpShlTests().run());
                passed &= runTest(() -> new IpShrAllTests().run());
                passed &= runTest(() -> new IpShrTests().run());
                passed &= runTest(() -> new IpSubTests().run());
                passed &= runTest(() -> new IpXorTests().run());
            } catch (IllegalState e) {
                console.print(e);
            }

            console.print("<<<< Finished Ip Op Tests <<<<<");
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
