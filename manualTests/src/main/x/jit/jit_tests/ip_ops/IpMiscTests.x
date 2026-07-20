/**
 * Tests for misc JIT in-place Ops.
 */
class IpMiscTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpMiscTests >>>>");
        testInPlaceAddAssign();
        console.print("<<<< Finished IpMiscTests <<<<<");
    }

    void testInPlaceAddAssign() {
        Test t = new Test(100);
        Int  n = t.test();
        assert n == 101;
        assert t.n == 101;
    }

    static class Test(Int n) {
        Int test() {
           Int n1 = ++n;
           return n1;
        }
    }

}
