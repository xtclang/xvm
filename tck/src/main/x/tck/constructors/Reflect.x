/**
 * Tests for construction via reflection.
 */
class Reflect {

    void run() {
        testUnassigned();
        testAssert();
        testAssertChain();
        testAnnotationChain();
    }

    /**
     * At the end of reflection-based instantiation all properties must be assigned.
     */
    @Test
    void testUnassigned() {
        static class Test(Int a1, String a2);

        try {
            Class<Test> clz = Test;
            assert (struct Test) structure := clz.allocate();
            structure.a1 = 4;
            // structure.a2  assignment is missing
            Test t = clz.instantiate(structure);
            assert;
        } catch (IllegalState e) {
            assert e.message.indexOf("a2");
        }
    }

    /**
     * At the end of reflection-based instantiation the validator (assert function) must be invoked.
     */
    @Test
    void testAssert() {
        static class Test(Int a1, String a2) {
            assert() {
                assert a2.size == a1;
            }
        }

        try {
            Class<Test> clz = Test;
            assert (struct Test) structure := clz.allocate();
            structure.a1 = 4;
            structure.a2 = "hello";
            Test t = clz.instantiate(structure);
            assert;
        } catch (IllegalState e) {
            assert e.message.indexOf("a2.size");
        }
    }

    /**
     * The validators must be invoked in the natural inheritance order (derived first).
     */
    @Test
    void testAssertChain() {
        static class Base(String s) {
            assert() {
                this.s = s + "-BA";
            }
        }
        static class Derived(String s)
                extends Base(s) {
            assert() {
                this.s = s + "-DA";
            }
        }

        Class<Derived> clz = Derived;
        assert (struct Derived) structure := clz.allocate();
        structure.s = "Test";
        Derived d = clz.instantiate(structure);
        assert d.s == "Test-DA-BA";
    }

    /**
     * The annotation's default constructor and assert must be called before the base assert.
     */
    @Test
    void testAnnotationChain() {
        static class Base(String s) {
            assert() {
                this.s = s + "-BA";
            }
        }
        static mixin M
                into Base {
            construct() {
                this.s = s + "-MC";
            }
            assert() {
                this.s = s + "-MA";
            }
        }

        Class<Base> clz = @M Base;
        assert (struct Base) structure := clz.allocate();
        structure.s = "Test";
        Base b = clz.instantiate(structure);
        assert b.s == "Test-MC-MA-BA";
    }
}
