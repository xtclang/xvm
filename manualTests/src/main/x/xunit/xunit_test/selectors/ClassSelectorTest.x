import xunit.DiscoveryConfiguration;
import xunit.Model;
import xunit.TestIdentifier;
import xunit.UniqueId;
import xunit.selectors.ClassSelector;

class ClassSelectorTest {
    @Test
    void shouldNotSelectPackageWithOnlyOmittedTests() {
        ClassSelector          selector = new ClassSelector(test_packages.OmittedTest);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 0;
    }

    @Test
    void shouldSelectSimpleClass() {
        Class                  clz      = test_packages.SimpleTest;
        ClassSelector          selector = new ClassSelector(clz);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 1;
        Model model = models[0];

        UniqueId expectedId = UniqueId.forClass(clz);
        assert:test model.uniqueId == expectedId;
        assert:test model.parentId == Null;
        assert:test model.displayName == "SimpleTest";
        assert:test model.identifier == new TestIdentifier(model.uniqueId, model.displayName);
        assert:test model.constructor != Null;
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
}
