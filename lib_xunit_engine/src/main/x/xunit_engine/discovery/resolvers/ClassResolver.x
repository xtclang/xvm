import discovery.selectors.ClassSelector;

import models.ContainerModel;

/**
 * A `SelectorResolver` that resolves classes.
 *
 * This resolvers specifically handles `ClassSelector` instances.
 */
service ClassResolver
        implements SelectorResolver {

    @Override
    conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector) {
        if (selector.is(ClassSelector)) {
            Class testClass;
            if (selector.testClass.is(Class)) {
                testClass = selector.testClass.as(Class);
            } else {
                TypeSystem typeSystem = this:service.typeSystem;
                if (Class clz := typeSystem.classForName(selector.testClass.as(String), True)) {
                    testClass = clz;
                } else {
                    return False;
                }
            }
            Selector[]     selectors = selectors.forChildren(testClass);
            ModelBuilder[] builders  = new Array();
            builders.add(new ContainerModel.Builder(testClass));
            return True, builders.freeze(True), selectors.freeze(True);
        }
        return False;
    }
}
