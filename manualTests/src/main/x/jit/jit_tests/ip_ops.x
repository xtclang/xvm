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
                passed = runTest(() -> new IpAddTests().run()) && passed;
                passed = runTest(() -> new IpAndTests().run()) && passed;
                passed = runTest(() -> new IpDivTests().run()) && passed;
                passed = runTest(() -> new IpModTests().run()) && passed;
                passed = runTest(() -> new IpMulTests().run()) && passed;
                passed = runTest(() -> new IpOrTests().run()) && passed;
                passed = runTest(() -> new IpShlTests().run()) && passed;
                passed = runTest(() -> new IpShrAllTests().run()) && passed;
                passed = runTest(() -> new IpShrTests().run()) && passed;
                passed = runTest(() -> new IpSubTests().run()) && passed;
                passed = runTest(() -> new IpXorTests().run()) && passed;
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
