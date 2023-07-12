import xunit_engine.DiscoveryConfiguration;
import xunit_engine.Model;

import xunit_engine.discovery.DefaultDiscoveryEngine;
import xunit_engine.discovery.Selector;
import xunit_engine.discovery.selectors;


class DefaultDiscoveryEngineTest {

    @Test
    void shouldDiscoverModelForPackage() {
        Package                pkg      = xunit_test.test_packages;
        assert Selector        selector := selectors.forPackage(pkg);
        DiscoveryConfiguration config   = DiscoveryConfiguration.builder()
                                                  .withSelector(selector)
                                                  .build();
        DefaultDiscoveryEngine engine   = new DefaultDiscoveryEngine();
        Model[]                models   = engine.discover(config);

        assert:test models.size == 1;
        assert:test models[0].children.size != 0;
    }

}