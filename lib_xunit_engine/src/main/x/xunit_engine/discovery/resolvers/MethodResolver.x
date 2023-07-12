import discovery.selectors.MethodSelector;

import models.ContainerModel;
import models.MethodModel;

import xunit.MethodOrFunction;

/**
 * A `SelectorResolver` that resolves classes.
 *
 * This resolvers specifically handles `MethodSelector` instances.
 */
service MethodResolver
        implements SelectorResolver {
    @Override
    conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector) {
        if (selector.is(MethodSelector)) {
            Class?             testClass   = Null;
            MethodOrFunction[] testMethods = [];

            if (selector.testClass.is(Class)) {
                testClass = selector.testClass.as(Class);
            }

            if (selector.testMethod.is(MethodOrFunction)) {
                MethodOrFunction testMethod = selector.testMethod.as(MethodOrFunction);
                if (testMethod.is(Test)) {
                    testMethods = [testMethod];
                    assert testClass.is(Class);
                }
            } else {
                String name = selector.testMethod.as(String);
                if (Int index := name.lastIndexOf('.')) {
                    String className  = name[0 ..<  index];
                    String methodName = name[index >..< name.size];
                    TypeSystem typeSystem = this:service.typeSystem;
                    if (Class clz := typeSystem.classForName(className, True)) {
                        Type type     = clz.toType();
                        var functions = type.functions.filter(f -> f.is(Test) && f.name == methodName);
                        var methods   = type.methods.filter(m -> m.is(Test) && m.name == methodName);
                        testMethods   = new Array();
                        testMethods.addAll(functions);
                        testMethods.addAll(methods);
                        testClass = clz;
                    }
                }
            }

            if (testClass.is(Class) && testMethods.size > 0) {
                ModelBuilder[] builders = new Array();
                builders.add(new ContainerModel.Builder(testClass));
                for (MethodOrFunction testMethod : testMethods) {
                    builders.add(new MethodModel.Builder(testClass, testMethod));
                }
                return True, builders.freeze(True), [];
            }
        }
        return False;
    }
}
