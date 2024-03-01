/**
 * More complicated construction tests.
 */
class Medium {

    void run() {
        testAssert();
        testFinalizer();
        testFinalizerChain();
    }

    /**
     * At the end of construction the assert() function must be invoked.
     */
    @Test
    void testAssert() {
        static class Test(Int a1, String a2) {
            assert() {
                assert a2.size == a1;
            }
        }

        try {
            Test t = new Test(4, "hello");
            assert;
        } catch (IllegalState e) {
            assert e.message.indexOf("a2.size");
        }
    }

    /**
     * At the end of construction the finalizer must be invoked.
     */
    @Test
    void testFinalizer() {
        static class Test(Int a1, String a2) {

            construct(Int a1, String a2) {
                this.a1 = a1;
                this.a2 = a2;
            } finally {
                this.a1++;
            }

            assert() {
                assert a2.size == a1;
            }
        }

        Test t = new Test(5, "hello");
        assert t.a1 == 6;
    }

    /**
     * The finalizers must be invoked in the inverse order of constructors execution - the "top"
     * constructor is called first and the "top" finalizer is called last, so the topmost class has
     * a full control over the state of the object.
     */
    @Test
    void testFinalizerChain() {
        static class Base(String s) {

            construct(String s) {
                this.s = s + "-BC";
            } finally {
                this.s += "-BF";
            }
        }
        static class Derived(String s)
            extends Base {

            construct(String s) {
                construct Base(s + "-DC");
            } finally {
                this.s += "-DF";
            }
        }
        Base b = new Derived("Test");
        assert b.s == "Test-DC-BC-BF-DF";
    }
}
