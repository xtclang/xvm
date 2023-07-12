import xunit_engine.DiscoveryConfiguration;
import xunit_engine.ModelBuilder;
import xunit_engine.UniqueId;

import xunit_engine.discovery.Selector;
import xunit_engine.discovery.resolvers.MethodResolver;
import xunit_engine.discovery.selectors.ClassSelector;
import xunit_engine.discovery.selectors.MethodSelector;
import xunit_engine.discovery.selectors.PackageSelector;

import xunit_engine.models.ContainerModel;

import xunit.MethodOrFunction;


class MethodResolverTest {

    @Test
    void shouldResolveMethodSelectorWithName() {
        Class                  clz        = SimpleTest;
        String                 className  = clz.path;
        String                 methodName = className + ".testOne";
        Selector               selector   = new MethodSelector(methodName);
        DiscoveryConfiguration config     = DiscoveryConfiguration.create();
        MethodResolver         resolver   = new MethodResolver();

        assert:test (ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(config, selector);

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