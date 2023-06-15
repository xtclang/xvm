import xunit.DiscoveryConfiguration;
import xunit.annotations.*;
import xunit.Model;
import xunit.Selector;
import xunit.UniqueId;
import xunit.selectors.ModuleSelector;

class ModuleSelectorTest {

    @Test
    void shouldFindModuleModel() {
        Module m = xunit_test;
        DiscoveryConfiguration cfg = DiscoveryConfiguration.create();
        ModuleSelector selector = new ModuleSelector(m);

        Model[] models = selector.select(cfg);
        assert:test models.size > 0;
    }
}
