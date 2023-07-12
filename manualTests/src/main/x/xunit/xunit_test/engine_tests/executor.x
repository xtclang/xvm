import xunit.ExtensionProvider;

import xunit_engine.Model;
import xunit_engine.UniqueId;

import xunit_engine.models.ContainerModel;
import xunit_engine.models.MethodModel;

/**
 * The `executor` package contains tests for the classes in the `xunit.executor`
 * package. This file contains common utilities used by executor tests.
 */
package executor {
    /**
     * Create a `ContainerModel` from a `Class`.
     *
     * @param clz         the `Class` to create a `ContainerModel` for
     * @param children    the child models for the container
     * @param extensions  any `ExtensionProvider`s to add to the `MethodModel`
     * @param fixture     the test fixture to execute tests against
     *
     * @return a `MethodModel` for the specified `Method`
     */
    static <Fixture> ContainerModel createContainerModel(Class<Fixture>      clz,
                                                         Model[]             children = [],
                                                         ExtensionProvider[] extensions = [],
                                                         Fixture?            fixture = Null) {
        Type     type = clz.toType();
        UniqueId id   =  UniqueId.forClass(clz);

        Model.Constructor constructor;
        if (fixture.is(Fixture)) {
            constructor = () -> fixture;
        } else {
            assert constructor := type.defaultConstructor();
        }

        return new  ContainerModel(id, clz, clz.name, constructor, extensions, children);
    }

    /**
     * Create a `MethodModel` from a `Method`.
     *
     * @param method      the `Method` to create a `MethodModel` for
     * @param fixture     the test fixture to execute the `Method` on
     * @param extensions  any `ExtensionProvider`s to add to the `MethodModel`
     *
     * @return a `MethodModel` for the specified `Method`
     */
    static <Fixture> MethodModel createMethodModel(Method<Fixture, Tuple<>, Tuple<>> method, Fixture? fixture = Null) {
        Type         type =  method.Target;
        assert Class clz  := type.fromClass();
        UniqueId     id   =  UniqueId.forClass(clz).append(Method, method.name);

        Model.Constructor constructor;
        if (fixture.is(Fixture)) {
            constructor = () -> fixture;
        } else {
            assert constructor := type.defaultConstructor();
        }

        return new MethodModel(id, clz, method, method.name, constructor);
    }
}