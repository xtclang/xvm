import discovery.selectors;
import discovery.selectors.PackageSelector;

import models.ContainerModel;

/**
 * A `SelectorResolver` that resolves packages.
 *
 * This resolvers specifically handles `PackageSelector` instances.
 */
service PackageResolver
        implements SelectorResolver {
    @Override
    conditional (ModelBuilder[], Selector[]) resolve(DiscoveryConfiguration config, Selector selector) {
        if (selector.is(PackageSelector)) {
            Class pkgClass;
            if (selector.testPackage.is(Package)) {
                Package testPackage = selector.testPackage.as(Package);
                pkgClass = &testPackage.actualClass.as(Class);
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
                ModelBuilder[] builders = new Array();
                builders.add(new ContainerModel.Builder(pkgClass));
                return True, builders.freeze(True), selectors.forChildren(pkgClass).freeze(True);
            }
        }
        return False;
    }
}