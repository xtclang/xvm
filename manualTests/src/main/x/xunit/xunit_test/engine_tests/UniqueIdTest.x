import xunit_engine.UniqueId;

class UniqueIdTest {

    @Test
    void shouldGetIdForModule() {
        Module   m  = xunit_test;
        UniqueId id = UniqueId.forObject(m);

        assert:test id.type == Module;
        assert:test id.segments.size == 1;
        assert:test id.parent() == False;
        assert:test id.path == &m.actualClass.path;
    }

    @Test
    void shouldGetIdForPackage() {
        Package  p  = xunit_test.test_packages;
        UniqueId id = UniqueId.forObject(p);

        assert:test id.segments.size == 2;
        assert:test id.type == Package;
        assert:test id.value == "test_packages";
        UniqueId parent;
        assert:test parent := id.parent();
        assert:test parent == UniqueId.forObject(xunit_test);
        assert:test id.path == &p.actualClass.path;
    }

    @Test
    void shouldGetIdForNestedPackage() {
        Package  p  = xunit_test.test_packages.simple_pkg;
        UniqueId id = UniqueId.forObject(p);

        assert:test id.segments.size == 3;
        assert:test id.type == Package;
        assert:test id.value == "simple_pkg";
        assert:test id.path == &p.actualClass.path;
        UniqueId parent;
        assert:test parent := id.parent();
        assert:test parent == UniqueId.forObject(xunit_test.test_packages);
    }

    @Test
    void shouldGetIdForClass() {
        Class    clz = xunit_test.test_packages.SimpleTest;
        UniqueId id  = UniqueId.forObject(clz);

        assert:test id.type == Class;
        assert:test id.value == "SimpleTest";
        assert:test id.segments.size == 3;
        assert:test id.path == clz.path;
        UniqueId parent;
        assert:test parent := id.parent();
        assert:test parent == UniqueId.forObject(xunit_test.test_packages);
    }

    @Test
    void shouldGetIdForClassInNestedPackage() {
        Class    clz = xunit_test.test_packages.before_and_after.BeforeAndAfterTests;
        UniqueId id  = UniqueId.forObject(clz);

        assert:test id.type == Class;
        assert:test id.segments.size == 4;
        assert:test id.path == clz.path;
        UniqueId parent;
        assert:test parent := id.parent();
        assert:test parent == UniqueId.forObject(xunit_test.test_packages.before_and_after);
    }

    @Test
    void shouldGetIdForMethod() {
        Class    clz    = xunit_test.test_packages.before_and_after.BeforeAndAfterTests;
        Method   method = xunit_test.test_packages.before_and_after.BeforeAndAfterTests.testOne;
        UniqueId id     = UniqueId.forObject(method);

        assert:test id.type == Method;
        assert:test id.value == "testOne";

        UniqueId parent;
        assert:test parent := id.parent();
        assert:test parent == UniqueId.forObject(clz);
    }
}