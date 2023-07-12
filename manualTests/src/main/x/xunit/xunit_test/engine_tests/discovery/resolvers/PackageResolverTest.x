import xunit_engine.DiscoveryConfiguration;
import xunit_engine.ModelBuilder;
import xunit_engine.UniqueId;

import xunit_engine.discovery.Selector;
import xunit_engine.discovery.resolvers.PackageResolver;
import xunit_engine.discovery.selectors.ClassSelector;
import xunit_engine.discovery.selectors.MethodSelector;
import xunit_engine.discovery.selectors.PackageSelector;

import xunit_engine.models.ContainerModel;


class PackageResolverTest {

    @Test
    void shouldResolvePackageSelectorWithClass() {
        Package                testPackage = test_packages.pkg_with_classes;
        Selector               selector    = new PackageSelector(testPackage);
        DiscoveryConfiguration config      = DiscoveryConfiguration.create();
        PackageResolver        resolver    = new PackageResolver();

        assert:test (ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(config, selector);
        assert:test builders.size == 1;
        assert:test builders[0].is(ContainerModel.Builder);
        assert:test builders[0].as(ContainerModel.Builder).uniqueId == UniqueId.forObject(testPackage);
        assert:test selectors.size == 2;
        assert:test selectors[0].is(ClassSelector);
        assert:test selectors[1].is(ClassSelector);

        var names = selectors.map(s -> s.as(ClassSelector).testClass.as(Class).name);
        assert:test names.contains("SimpleTestOne");
        assert:test names.contains("SimpleTestTwo");
    }

    @Test
    void shouldResolvePackageSelectorWithPackageName() {
        Package                testPackage = test_packages.pkg_with_classes;
        Selector               selector    = new PackageSelector("test_packages.pkg_with_classes");
        DiscoveryConfiguration config      = DiscoveryConfiguration.create();
        PackageResolver        resolver    = new PackageResolver();

        assert:test (ModelBuilder[] builders, Selector[] selectors) := resolver.resolve(config, selector);
        assert:test builders.size == 1;
        assert:test builders[0].is(ContainerModel.Builder);
        assert:test builders[0].as(ContainerModel.Builder).uniqueId == UniqueId.forObject(testPackage);
        assert:test selectors.size == 2;
        assert:test selectors[0].is(ClassSelector);
        assert:test selectors[1].is(ClassSelector);

        var names = selectors.map(s -> s.as(ClassSelector).testClass.as(Class).name);
        assert:test names.contains("SimpleTestOne");
        assert:test names.contains("SimpleTestTwo");
    }

    @Test
    void shouldNotResolveInvalidPackageName() {
        Selector               selector = new PackageSelector("not.a.package");
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        PackageResolver        resolver = new PackageResolver();
        assert:test resolver.resolve(config, selector) == False;
    }

    @Test
    void shouldNotResolveClassSelector() {
        Selector               selector = new ClassSelector(test_packages.SimpleTest);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        PackageResolver        resolver = new PackageResolver();
        assert:test resolver.resolve(config, selector) == False;
    }

    @Test
    void shouldNotResolveMethodSelector() {
        Selector               selector = new MethodSelector(test_packages.SimpleTest, test_packages.SimpleTest.testOne);
        DiscoveryConfiguration config   = DiscoveryConfiguration.create();
        PackageResolver        resolver = new PackageResolver();
        assert:test resolver.resolve(config, selector) == False;
    }
}