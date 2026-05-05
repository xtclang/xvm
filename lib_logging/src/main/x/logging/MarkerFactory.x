/**
 * Corresponds to `org.slf4j.MarkerFactory` (the static-method facade in SLF4J) plus
 * `org.slf4j.IMarkerFactory` (the interface SLF4J's facade delegates to). Logback's
 * marker-aware filters consume the same marker objects.
 *
 * Factory for `Marker` instances. Mirrors `org.slf4j.MarkerFactory`.
 *
 * `getMarker(name)` is _interning_: two calls with the same name return the same instance.
 * `getDetachedMarker(name)` always returns a fresh instance, useful when a caller wants a
 * marker that is not visible to other callers.
 *
 * This is intentionally a stateful `class`, not a `service`: markers are mutable while a
 * caller builds a marker DAG, and service-boundary passing would freeze/copy them before
 * the caller could rely on SLF4J-style identity.
 */
class MarkerFactory {

    private Map<String, Marker> markers = new HashMap();

    /**
     * Get (creating if necessary) the canonical marker with the supplied name. Subsequent calls
     * with the same `name` return the same marker.
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
