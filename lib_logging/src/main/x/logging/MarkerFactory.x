/**
 * Corresponds to `org.slf4j.MarkerFactory` (the static-method facade in SLF4J) plus
 * `org.slf4j.IMarkerFactory` (the interface SLF4J's facade delegates to). Logback's
 * marker-aware filters consume the same marker objects.
 *
 * Factory for `Marker` instances. Mirrors `org.slf4j.MarkerFactory`.
 *
 * `getMarker(name)` is _interning_: two calls with the same name return the same
 * instance. `getDetachedMarker(name)` always returns a fresh instance, useful when a
 * caller wants a marker that is not visible to other callers.
 *
 * # Why a `class`, not a `service`
 *
 * The natural Ecstasy answer to "make this thread-safe" is `service`. We deliberately
 * do not pick that here, because of an interaction between three facts:
 *
 *  - `BasicMarker` is a *mutable* `class` — `add(child)` builds the DAG in place.
 *  - The SLF4J idiom is `factory.getMarker("X").add(child)`, which mutates the
 *    interned marker directly after retrieval.
 *  - Ecstasy auto-freezes any `Freezable` value that crosses a service boundary on
 *    the way out.
 *
 * Together, those rules mean a `service MarkerFactory.getMarker(...)` would return a
 * frozen `BasicMarker` to its caller, and the next `marker.add(...)` call would throw
 * `ReadOnly`. That contradicts the SLF4J pattern and breaks every existing test and
 * `manualTests/TestLogging.x` call site.
 *
 * # Thread-safety contract
 *
 * Because this is a `class` with a mutable internal `HashMap`, **a single
 * `MarkerFactory` instance is not safe to share across fibers**. The supported pattern
 * is one factory per component (per service, per request, per test). The existing
 * test suite (`MarkerFactoryTest`, `MarkerTest`) and `manualTests/TestLogging.x` follow
 * this pattern: each scope constructs its own factory.
 *
 * If a future user genuinely needs a process-global, fiber-safe marker registry, the
 * right shape is a separate `service MarkerRegistry` whose API folds DAG mutation
 * into service methods (e.g. `addReference(parentName, childName)`) so the in-service
 * `BasicMarker` instances stay live and never cross the freeze boundary. That is
 * tracked in `doc/logging/roadmap.md` rather than retrofitted onto this type, because
 * collapsing both shapes into one type either gives up the SLF4J ergonomics here or
 * gives up safe global sharing there.
 */
class MarkerFactory {

    private Map<String, Marker> markers = new HashMap();

    /**
     * Get (creating if necessary) the canonical marker with the supplied name.
     * Subsequent calls with the same `name` return the same marker.
     *
     * Thread safety: the underlying `HashMap.computeIfAbsent` is not concurrent. Use
     * one `MarkerFactory` per fiber/component (see the class doc comment).
     */
    Marker getMarker(String name) {
        return markers.computeIfAbsent(name, () -> new BasicMarker(name));
    }

    /**
     * Construct a fresh marker that is not registered in the factory's interning map.
     */
    Marker getDetachedMarker(String name) {
        return new BasicMarker(name);
    }

    /**
     * True iff a marker with the supplied name has previously been interned.
     */
    Boolean exists(String name) {
        return markers.contains(name);
    }
}
