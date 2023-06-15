import test_packages.pkg_with_classes.SimpleTestOne;
import test_packages.pkg_with_classes.SimpleTestTwo;
import xunit.DiscoveryConfiguration;
import xunit.Model;
import xunit.TestIdentifier;
import xunit.UniqueId;
import xunit.selectors.PackageSelector;

class PackageSelectorTest {

    @Test
    void shouldNotSelectPackageWithOnlyOmittedTests() {
        PackageSelector        selector = new PackageSelector(test_packages.omitted_pkg);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 0;
    }

    @Test
    void shouldSelectSimplePackage() {
        Package                pkg      = test_packages.simple_pkg;
        Class                  clz      = &pkg.actualClass;
        PackageSelector        selector = new PackageSelector(pkg);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 1;
        Model model = models[0];

        UniqueId expectedId = UniqueId.forClass(clz);
        assert:test model.uniqueId == expectedId;
        assert:test model.parentId == Null;
        assert:test model.displayName == "simple_pkg";
        assert:test model.identifier == new TestIdentifier(model.uniqueId, model.displayName);
        assert:test model.constructor == Null;
        assert:test model.extensionProviders.size == 0;
        assert:test model.priority == 0;
        assert:test model.isContainer;
        assert:test model.templateFactories.size == 0;
        assert:test model.isRoot();
        assert:test model.mayRegisterTests() == False;
        assert:test Model.containsTests(model);

        UniqueId idOne = expectedId.append(UniqueId.SEGMENT_METHOD, "testOne");
        UniqueId idTwo = expectedId.append(UniqueId.SEGMENT_METHOD, "testTwo");

        assert:test model.children.size == 2;
        assert:test model.children.any(m -> m.uniqueId == idOne);
        assert:test model.children.any(m -> m.uniqueId == idTwo);

        Set<Model> set = model.getDescendants();
        assert:test set.size == 2;
        assert:test set.any(m -> m.uniqueId == idOne);
        assert:test set.any(m -> m.uniqueId == idTwo);
    }

    @Test
    void shouldSelectPackageWithClasses() {
        Package                pkg      = test_packages.pkg_with_classes;
        Class                  clz      = &pkg.actualClass;
        PackageSelector        selector = new PackageSelector(pkg);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 1;
        Model model = models[0];

        UniqueId expectedId = UniqueId.forClass(clz);
        assert:test model.uniqueId == expectedId;
        assert:test model.parentId == Null;
        assert:test model.displayName == "pkg_with_classes";
        assert:test model.identifier == new TestIdentifier(model.uniqueId, model.displayName);
        assert:test model.constructor == Null;
        assert:test model.extensionProviders.size == 0;
        assert:test model.priority == 0;
        assert:test model.isContainer;
        assert:test model.templateFactories.size == 0;
        assert:test model.isRoot();
        assert:test model.mayRegisterTests() == False;
        assert:test Model.containsTests(model);

        UniqueId idOne = UniqueId.forClass(SimpleTestOne);
        UniqueId idTwo = UniqueId.forClass(SimpleTestTwo);

        assert:test model.children.size == 2;
        assert:test model.children.any(m -> m.uniqueId == idOne);
        assert:test model.children.any(m -> m.uniqueId == idTwo);

        Set<Model> set = model.getDescendants();
        assert:test set.size == 6;
        assert:test set.any(m -> m.uniqueId == idOne);
        assert:test set.any(m -> m.uniqueId == idTwo);
    }
}