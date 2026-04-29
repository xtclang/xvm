/**
 * Corresponds to `org.slf4j.Marker` (SLF4J), with the same semantics as
 * `ch.qos.logback.classic.spi.Marker` (Logback) and the marker concept in Log4j 2.
 *
 * A `Marker` is a named tag that can be attached to a log call to give downstream filters and
 * appenders a structured way to route or suppress events without having to grep on message
 * content. Mirrors `org.slf4j.Marker`.
 *
 * Markers can be composed: a marker may have references to other markers (forming a DAG), so
 * that a single specific marker can be matched against a broader category. `contains` does the
 * transitive check.
 *
 * # XDK-idiomatic interface design
 *
 * `Marker` extends three core XDK interfaces:
 *
 *   - **[Freezable]** — markers are carried on `LogEvent` (a `const`); a `const` can only carry
 *     freezable fields. Anything that may end up on an event must be freezable. This is also
 *     the property that lets a `Marker` cross a service boundary safely (e.g. when the active
 *     `LogSink` is a service): the runtime auto-freezes shareable values at the boundary.
 *   - **[Stringable]** — markers are routinely rendered into formatted log lines and JSON
 *     payloads. Implementing `Stringable` lets layouts and encoders pre-size their buffers via
 *     `estimateStringLength()` and avoid extra `toString()` allocations on the hot path. Other
 *     XDK libs (e.g. `Path`, `URI`, `IPAddress`) follow the same pattern.
 *   - **[Hashable]** — sinks and filters frequently key off markers (a marker-aware filter
 *     keeps a `Set<Marker>` of "interesting" categories). `Hashable` makes that natural and
 *     makes markers usable as `Map` keys without bridging through their name.
 *
 * The default implementation in this module ([BasicMarker]) is intentionally minimal — the
 * in-memory marker shipped here exists primarily so that user code that *uses* markers
 * compiles and runs against the default sink (which mostly ignores them). Real filtering is
 * the job of richer sinks (see `doc/logging/LOGBACK_INTEGRATION.md`).
 */
interface Marker
        extends Freezable
        extends Stringable
        extends Hashable {

    /**
     * The marker's name. Stable identifier; markers with the same name are considered equal.
     */
    @RO String name;

    /**
     * Add a child marker as a reference. Idempotent: adding the same reference twice is a
     * no-op. Throws `ReadOnly` on a frozen marker.
     */
    void add(Marker reference);

    /**
     * Remove a previously added reference. Returns True iff the reference was present.
     * Throws `ReadOnly` on a frozen marker.
     */
    Boolean remove(Marker reference);

    /**
     * True iff this marker has any child references.
     */
    @RO Boolean hasReferences;

    /**
     * The direct child references of this marker.
     */
    @RO Iterator<Marker> references;

    /**
     * Transitive containment check: True iff this marker is `other`, or has a (possibly
     * indirect) reference to a marker named the same as `other`.
     */
    Boolean contains(Marker other);

    /**
     * Transitive containment check by name.
     */
    Boolean containsName(String name);

    // ---- Stringable -----------------------------------------------------------------------

    @Override
    Int estimateStringLength() = name.size;

    @Override
    Appender<Char> appendTo(Appender<Char> buf) = name.appendTo(buf);

    // ---- Hashable -------------------------------------------------------------------------

    @Override
    static <CompileType extends Marker> Int64 hashCode(CompileType value) {
        return value.name.hashCode();
    }

    @Override
    static <CompileType extends Marker> Boolean equals(CompileType value1, CompileType value2) {
        return value1.name == value2.name;
    }
}
