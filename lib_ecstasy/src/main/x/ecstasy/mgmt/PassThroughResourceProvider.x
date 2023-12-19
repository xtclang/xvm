import reflect.Injector;

/**
 * PassThroughResourceProvider is a `ResourceProvider` implementation that supplies injected
 * resources as a pass-through from the parent onto the child container.
 */
service PassThroughResourceProvider
             implements ResourceProvider {
    @Inject Injector injector;

    @Override
    Supplier getResource(Type type, String name) {
        import annotations.InjectedRef;

        return (InjectedRef.Options opts) -> {
            return injector.inject(type, name, opts);
        };
    }
}