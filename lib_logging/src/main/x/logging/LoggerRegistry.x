/**
 * Corresponds to the role played by `org.slf4j.ILoggerFactory` impls (Logback's
 * `LoggerContext`, Log4j 2's `LoggerContext`): the central name-keyed cache that
 * makes repeated `getLogger("a.b")` / `parent.named("b")` calls return the same
 * `Logger` instance.
 *
 * A name-keyed intern cache for `Logger` instances. `BasicLogger.named(child)` consults
 * an attached registry — when present — and returns the cached logger for the resulting
 * `"<parent>.<child>"` name; without a registry, each call allocates a fresh logger.
 *
 * # Why interning belongs in a separate service
 *
 * `BasicLogger` is a `const` (so the runtime can hand it back from `@Inject Logger`
 * without a service-boundary hop, see `BasicLogger.x`). A `const` cannot carry the
 * mutable hash map a cache requires — the field would not be `Passable`. Splitting the
 * cache out into a `service` keeps the logger immutable while still giving SLF4J's
 * "same name → same Logger instance" semantics.
 *
 * # Scope
 *
 * Each registry is bound to a single [LogSink]. Loggers from different sinks live in
 * different registries (or in no registry at all): `LoggerFactory` constructs one
 * registry per active sink internally; user code that wants identity-stable loggers
 * across an explicit non-default sink can construct its own:
 *
 *      service ListLogSink testSink = new ListLogSink();
 *      LoggerRegistry      registry = new LoggerRegistry(testSink);
 *      Logger              root     = registry.ensure("root");
 *      assert &root == &registry.ensure("root");      // same instance
 *      assert &root.named("a.b") == &registry.ensure("root.a.b");
 */
service LoggerRegistry(LogSink sink) {

    private Map<String, Logger> cache = new HashMap();

    /**
     * Return the logger for `name`, creating it on first access. Repeated calls with the
     * same `name` return the identical instance.
     */
    Logger ensure(String name) {
        return cache.computeIfAbsent(name, () -> new BasicLogger(name, sink, this));
    }

    /**
     * Discard all cached loggers. Primarily useful for tests that want a clean slate;
     * production code typically never calls this.
     */
    void reset() {
        cache.clear();
    }
}
