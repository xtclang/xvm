import discovery.selectors.ClassSelector;

import models.ContainerModel;

/**
 * A `SelectorResolver` that resolves classes.
 *
 * This resolvers specifically handles `ClassSelector` instances.
 */
const ClassResolver
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

            Type type = testClass.toType();
            if (type.is(Type<Enum>)) {
                // We do not support Enums as test containers
                // ToDo JK Why not? The recursive child classes break discovery.
                return False;
            }

            try {
                Selector[] childSelectors = selectors.forChildren(testClass);
                return True, [ContainerModel.builder(testClass)], childSelectors;
            } catch (Exception e) {
                @Inject Console console;
                console.print($|WARNING: XUnit discovery, failed to obtain children of \
                               |"{testClass}" due to "{e.text}"
                               );
                return True, [], [];
            }
        }
        return False;
    }
}
