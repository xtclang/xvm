import xunit.ExtensionProvider;
import xunit.annotations.AfterAll;
import xunit.annotations.BeforeAll;
import xunit.annotations.ExtensionMixin;

/**
 * Common utilities used by the XUnit engine.
 */
package utils {
    /**
     * Find the constructor that should be used to create instances of
     * the specified type.
     *
     * If any constructor is annotated with `@Test` that constructor will be used.
     * If multiple constructors are annotated with `@Test`, the first constructor
     * found will be used, which may be non-deterministic.
     *
     * If not constructors are annotated with `@Test` the default constructor will be used,
     * which is either a no-arg constructor, or a constructor where all parameters have
     * default values.
     *
     * @param type  the `Type` to find the constructor for
     *
     * @return the constructor to use, or `Null` if no constructor can be found for the type.
     */
    Model.Constructor? findTestConstructor(Type type) {
        Model.Constructor? constructor = Null;
        for (Function<Tuple, <>> c : type.constructors) {
            if (c.is(Test)) {
                constructor = c.as(Model.Constructor);
                break;
            }
            else if (c.requiredParamCount == 0) {
                constructor = c.as(Type.Constructor);
            }
        }
        return constructor;
    }

    /**
     * Find any `ExtensionProvider`s in the specified `Class`.
     *
     * @return the array of `ExtensionProvider`s declared in the `Class`
     *         or an empty array if the class has no providers
     */
    immutable ExtensionProvider[] findExtensions(Class clz) {
        Type                type      = clz.toType();
        ExtensionProvider[] providers = new Array();
        findExtensions(type.functions,  providers);
        findExtensions(type.methods,    providers);
        findExtensions(type.properties, providers);
        findExtensions(type.constants,  providers);
        return providers.freeze(True);
    }

    private void findExtensions(Iterable iter, ExtensionProvider[] providers) {
        for (Object o : iter) {
            if (o.is(ExtensionMixin)) {
                providers.addAll(o.getExtensionProviders());
            }
        }
    }
}