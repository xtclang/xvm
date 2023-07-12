/**
 * An `ExtensionProvider` that wraps a `Property`.
 *
 * The `Property` type should be an `Extension`.
 */
const PropertyExtensionProvider(Property<Object, Object, Ref<Object>> property)
        implements ExtensionProvider {

    @Override String name.get() {
        return property.name;
    }

    @Override
    Extension[] getExtensions(ExecutionContext context) {
        Extension extension;
        if (Object referent := property.isConstant()) {
            return [referent.as(Extension)];
        } else {
            Object? fixture = context.testFixture;
            if (fixture.is(property.Target)) {
                return [property.get(fixture).as(Extension)];
            }
        }
        return [];
    }
}