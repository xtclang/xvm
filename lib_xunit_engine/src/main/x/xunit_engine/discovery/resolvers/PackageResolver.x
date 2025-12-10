import discovery.selectors;
import discovery.selectors.PackageSelector;

import models.ContainerModel;

/**
 * A `SelectorResolver` that resolves packages.
 *
 * This resolvers specifically handles `PackageSelector` instances.
 */
const PackageResolver
        implements SelectorResolver {
    @Override
    conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector) {
        if (selector.is(PackageSelector)) {
            Class pkgClass;
            var testPackage = selector.testPackage;
            if (testPackage.is(Package)) {
                pkgClass = &testPackage.class;
            } else {
                TypeSystem typeSystem = this:service.typeSystem;
                if (Class clz := typeSystem.classForName(selector.testPackage.as(String), True)) {
                    pkgClass = clz;
                } else {
                    return False;
                }
            }

            Type type = pkgClass.toType();
            if (type.isA(Package)) {
                if (Object o := pkgClass.isSingleton()) {
                    if (!selector.allowImport && o.as(Package).isModuleImport()) {
                        return False;
                    }
                } else if (function Object () fn := type.defaultConstructor()) {
                    Package pkg = fn().as(Package);
                    if (pkg.isModuleImport()) {
                        return False;
                    }
                } else {
                    return False;
                }
                ModelBuilder[] builders = type.is(Type<Module>) ? []
                        : [ContainerModel.builder(pkgClass)];

                return True, builders, selectors.forChildren(pkgClass);
            }
        }
        return False;
    }
}