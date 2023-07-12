import xunit_engine.DiscoveryConfiguration;
import xunit_engine.ModelBuilder;
import xunit_engine.UniqueId;

import xunit_engine.discovery.Selector;
import xunit_engine.discovery.resolvers.ClassResolver;
import xunit_engine.discovery.selectors.ClassSelector;
import xunit_engine.discovery.selectors.MethodSelector;
import xunit_engine.discovery.selectors.PackageSelector;

import xunit_engine.models.ContainerModel;

import xunit.MethodOrFunction;

class ClassResolverTest {

    @Test
    void shouldResolveClassSelectorWithClass() {
        Class                  clz      = SimpleTest;
        Selector               selector = new ClassSelector(clz);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        ClassResolver          resolver = new ClassResolver();

        assert:test (ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(config, selector);
        assert:test builders.size == 1;
        assert:test builders[0].is(ContainerModel.Builder);
        assert:test builders[0].as(ContainerModel.Builder).uniqueId == UniqueId.forClass(clz);
        assert:test selectors.size == 2;
        assert:test selectors[0].is(MethodSelector);
        assert:test selectors[0].as(MethodSelector).testClass == clz;
        assert:test selectors[1].is(MethodSelector);
        assert:test selectors[1].as(MethodSelector).testClass == clz;

        var names = selectors.map(s -> s.as(MethodSelector).testMethod.as(MethodOrFunction).name);
        assert:test names.contains("testOne");
        assert:test names.contains("testTwo");
    }

    @Test
    void shouldResolveClassSelectorWithClassName() {
        Class                  clz      = SimpleTest;
        String                 name     = clz.path;
        Selector               selector = new ClassSelector(name);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        ClassResolver          resolver = new ClassResolver();

        assert:test (ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(config, selector);
        assert:test builders.size == 1;
        assert:test builders[0].is(ContainerModel.Builder);
        assert:test builders[0].as(ContainerModel.Builder).uniqueId == UniqueId.forClass(clz);
        assert:test selectors.size == 2;
        assert:test selectors[0].is(MethodSelector);
        assert:test selectors[0].as(MethodSelector).testClass == clz;
        assert:test selectors[1].is(MethodSelector);
        assert:test selectors[1].as(MethodSelector).testClass == clz;

        var names = selectors.map(s -> s.as(MethodSelector).testMethod.as(MethodOrFunction).name);
        assert:test names.contains("testOne");
        assert:test names.contains("testTwo");
    }

    @Test
    void shouldNotResolveInvalidClassName() {
        Selector               selector = new ClassSelector("bad.TestClass");
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        ClassResolver          resolver = new ClassResolver();
        assert:test resolver.resolve(config, selector) == False;
    }

    @Test
    void shouldNotResolvePackageSelector() {
        Selector               selector = new PackageSelector(test_packages);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        ClassResolver          resolver = new ClassResolver();
        assert:test resolver.resolve(config, selector) == False;
    }

    @Test
    void shouldNotResolveMethodSelector() {
        Selector               selector = new MethodSelector(SimpleTest, SimpleTest.testOne);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        ClassResolver          resolver = new ClassResolver();
        assert:test resolver.resolve(config, selector) == False;
    }

    @MockTest
    static class SimpleTest {
        @Test
        void testOne() {
        }

        @Test
        void testTwo() {
        }
    }
}