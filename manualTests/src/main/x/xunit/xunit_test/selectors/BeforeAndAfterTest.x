import test_packages.before_and_after.*;
import xunit.DiscoveryConfiguration;
import xunit.ExecutionContext;
import xunit.Extension;
import xunit.Model;
import xunit.TestIdentifier;
import xunit.UniqueId;
import xunit.extensions.AfterAllFunction;
import xunit.extensions.AfterEachFunction;
import xunit.extensions.BeforeAllFunction;
import xunit.extensions.BeforeEachFunction;
import xunit.selectors.PackageSelector;

/**
 * Test selectors with before and after annotated methods
 */
class BeforeAndAfterTest {

    @Test
    void shouldSelectBeforeAndAfterFromPackage() {
        PackageSelector        selector = new PackageSelector(test_packages.before_and_after);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 1;

        Model model = models[0];
        assert:test model.extensionProviders.size == 8;

        ExecutionContext      ctx        = ExecutionContext.create(model);
        Collection<Extension> extensions = model.extensionProviders.flatMap(p -> p.getExtensions(ctx));
        assert:test extensions.size == 8;

        Collection<AfterAllFunction> afterAll = extensions.filter(e -> e.is(AfterAllFunction))
                .map(e -> e.as(AfterAllFunction));
        assert:test afterAll.size == 2;

        Collection<AfterEachFunction> afterEach = extensions.filter(e -> e.is(AfterEachFunction))
                .map(e -> e.as(AfterEachFunction));
        assert:test afterEach.size == 2;

        Collection<BeforeAllFunction> beforeAll = extensions.filter(e -> e.is(BeforeAllFunction))
                .map(e -> e.as(BeforeAllFunction));
        assert:test beforeAll.size == 2;

        Collection<BeforeEachFunction> beforeEach = extensions.filter(e -> e.is(BeforeEachFunction))
                .map(e -> e.as(BeforeEachFunction));
        assert:test beforeEach.size == 2;
    }

    @Test
    void shouldSelectBeforeAndAfterFromClass() {
        PackageSelector        selector = new PackageSelector(test_packages.before_and_after);
        DiscoveryConfiguration cfg      = DiscoveryConfiguration.create();
        Model[]                models   = selector.select(cfg);

        assert:test models.size == 1;

        Model packageModel = models[0];
        @Inject Console console;
        console.print($"{packageModel.children}");
        
        assert:test packageModel.children.size == 1;

        Model model = packageModel.children[0];
        assert:test model.uniqueId == UniqueId.forClass(BeforeAndAfterTests);
        assert:test model.extensionProviders.size == 8;

        ExecutionContext      ctx        = ExecutionContext.create(model);
        Collection<Extension> extensions = model.extensionProviders.flatMap(p -> p.getExtensions(ctx));
        assert:test extensions.size == 8;

        Collection<AfterAllFunction> afterAll = extensions.filter(e -> e.is(AfterAllFunction))
                .map(e -> e.as(AfterAllFunction));
        assert:test afterAll.size == 2;

        Collection<AfterEachFunction> afterEach = extensions.filter(e -> e.is(AfterEachFunction))
                .map(e -> e.as(AfterEachFunction));
        assert:test afterEach.size == 2;

        Collection<BeforeAllFunction> beforeAll = extensions.filter(e -> e.is(BeforeAllFunction))
                .map(e -> e.as(BeforeAllFunction));
        assert:test beforeAll.size == 2;

        Collection<BeforeEachFunction> beforeEach = extensions.filter(e -> e.is(BeforeEachFunction))
                .map(e -> e.as(BeforeEachFunction));
        assert:test beforeEach.size == 2;
    }
}