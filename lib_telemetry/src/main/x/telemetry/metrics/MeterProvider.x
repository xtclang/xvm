/**
 * The entry point to the metrics API.
 *
 * A `MeterProvider` is the top-level object that application code configures once
 * (typically at startup) and then uses to obtain [Meter] instances. Each distinct
 * combination of `(name, version, schemaUrl, attributes)` identifies a unique
 * [InstrumentationScope].
 */
interface MeterProvider {
    /**
     * Returns a [Meter] for ab instrumentation scope identity created for the specified [Type].
     * If a `Meter` with the same `(name, version, schemaUrl, attributes)` combination has already
     * been created by this provider, it may return the same instance.
     *
     * @param type        the [Type] to use to create an instrumentation scope
     * @param attributes  (optional) additional attributes associated with the scope
     */
    Meter getMeter(Type type, Attributes attributes = []) {
        return getMeter(InstrumentationScope.from(type, attributes));
    }

    /**
     * Returns a [Meter] for the given instrumentation scope identity. If a `Meter` with
     * the same `(name, version, schemaUrl, attributes)` combination has already been
     * created by this provider, it may return the same instance.
     *
     * @param name        the instrumentation scope name; use a reverse-DNS style identifier
     *                    such as `"com.example.mylib"`
     * @param version     (optional) the version of the instrumentation scope
     * @param schemaUrl   (optional) the schema URL for the instrumentation scope
     * @param attributes  (optional) additional attributes associated with the scope
     */
    Meter getMeter(String     name,
                   String?    version    = Null,
                   String?    schemaUrl  = Null,
                   Attributes attributes = []) {
        return getMeter(new InstrumentationScope(name, version, schemaUrl, attributes));
    }

    /**
     * Returns a [Meter] for the given instrumentation scope identity. If a `Meter` with
     * the same `(name, version, schemaUrl, attributes)` combination has already been
     * created by this provider, it may return the same instance.
     *
     * @param name        the instrumentation scope name; use a reverse-DNS style identifier
     *                    such as `"com.example.mylib"`
     * @param version     (optional) the version of the instrumentation scope
     * @param schemaUrl   (optional) the schema URL for the instrumentation scope
     * @param attributes  (optional) additional attributes associated with the scope
     */
    Meter getMeter(InstrumentationScope scope);

    /**
     * Flush all pending measurements to every registered [MetricReader]. No-op if no
     * readers are registered or if [shutdown] has already been called.
     */
    void forceFlush();

    /**
     * Shut down all registered [MetricReader]s. After this call, [getMeter] returns a
     * no-op [Meter] and subsequent [forceFlush] calls are no-ops.
     */
    void shutdown();
}
