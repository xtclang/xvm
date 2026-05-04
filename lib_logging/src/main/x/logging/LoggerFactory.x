/**
 * Corresponds to `org.slf4j.LoggerFactory` (the static accessor) and
 * `org.slf4j.ILoggerFactory` (the interface that bindings implement). The escape-hatch
 * accessor for code that cannot use `@Inject`.
 *
 * Static accessor for `Logger` instances, for code that cannot use `@Inject`.
 *
 * Mirrors `org.slf4j.LoggerFactory`. The first call into `getLogger` resolves the active
 * `LogSink` (via injection) and caches it; subsequent calls return name-keyed `Logger`
 * instances backed by that sink.
 *
 * Library code that wants `@Inject`-free acquisition should typically use:
 *
 *      static Logger logger = LoggerFactory.getLogger("com.example.foo");
 *
 * or, when a class object is in scope:
 *
 *      static Logger logger = LoggerFactory.getLogger(MyClass);
 */
service LoggerFactory {

    /**
     * The active default backend. This is intended to be supplied by the host
     * container; if no richer sink is registered, the runtime can fall back to the
     * console sink.
     */
    @Inject LogSink defaultSink;

    /**
     * Lazily-created name cache scoped to the injected sink. Mirrors SLF4J's
     * `ILoggerFactory` identity-stability rule without creating JVM-global state.
     */
    private @Lazy LoggerRegistry registry.calc() {
        return new LoggerRegistry(defaultSink);
    }

    /**
     * Get the logger for `name`, creating it on first access. Repeated calls with the
     * same name return the identical `Logger`; `getLogger("a.b")` and
     * `getLogger("a").named("b")` return the same instance.
     */
    Logger getLogger(String name) {
        return registry.ensure(name);
    }

    /**
     * Convenience: `getLogger(clz.path)`.
     */
    Logger getLogger(Class clz) {
        return getLogger(clz.path);
    }
}
