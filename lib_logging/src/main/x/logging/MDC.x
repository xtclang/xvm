/**
 * Corresponds to `org.slf4j.MDC` (and the underlying `org.slf4j.spi.MDCAdapter`).
 * Logback's `LogbackMDCAdapter` and Log4j 2's `ThreadContext` cover the same concept.
 *
 * Mapped Diagnostic Context — a per-fiber string-keyed scratchpad that sinks can include in
 * formatted output. Bindings made by one fiber are visible to its callees and to fibers it
 * spawns (matching SLF4J's `InheritableThreadLocal`-style propagation), but mutations from
 * a child fiber do **not** bleed back to the parent: each fiber's `put` / `remove` / `clear`
 * affects only its own logical-thread-of-execution.
 *
 * Implementation: backed by a single static [ecstasy.SharedContext] holding an `immutable Map`. Every
 * mutating operation copy-on-writes a new immutable map and rebinds it via
 * `ecstasy.SharedContext.withValue`. Because the runtime stores `ecstasy.SharedContext` tokens on the fiber
 * (not the service), and child fibers receive their own copy of the parent's token map at
 * spawn time, the child's `withValue` registers on the child's chain only — the parent's
 * binding stays untouched. The shared *value* (the immutable map) is safe because there is
 * no mutation to leak.
 *
 * `MDC` is intentionally a `const`, not a `service`. Methods on a `const` execute on the
 * caller's service, which is where the fiber's token chain lives. If `MDC` were a service,
 * each call would cross into a fresh service-side fiber and `withValue` would register on
 * that fiber instead of the caller's — invisible to the caller after the call returns.
 *
 * Usage matches SLF4J's flat `put` / `remove` / `clear` API:
 *
 *     mdc.put("requestId", id);
 *     try {
 *         logger.info("processing");
 *     } finally {
 *         mdc.remove("requestId");
 *     }
 *
 * The token chain grows by one entry per mutation. For typical request-scoped MDC usage
 * (a handful of `put`s at the request boundary, a `clear` at the end) this is bounded by
 * the request fiber's lifetime. Long-lived fibers that mutate MDC frequently will retain a
 * proportional chain.
 */
const MDC {

    /**
     * The fiber-local backing store. The default value `[]` is the empty immutable map, so
     * `currentMap()` never has to special-case the unbound state.
     */
    /**
     * The empty immutable map used as the default value of [mapContext] when no fiber
     * has yet bound an MDC entry. Naming it lets the static initializer infer the type
     * argument for `SharedContext`.
     */
    private static immutable Map<String, String> emptyMap = [];

    private static ecstasy.SharedContext<immutable Map<String, String>> mapContext =
            new ecstasy.SharedContext("mdc", emptyMap);

    /**
     * Read the current fiber's view. Always returns an immutable map; the caller can hand
     * the result to a sink without further copying.
     */
    private immutable Map<String, String> currentMap() {
        return mapContext.hasValue() ?: [];
    }

    /**
     * Build the next immutable map by copying entries other than `dropKey` and optionally
     * appending `(addKey, addValue)`. One allocation per mutation; O(n) in current size.
     */
    private immutable Map<String, String> derive(String? dropKey, String? addKey, String? addValue) {
        HashMap<String, String> next = new HashMap();
        for ((String k, String v) : currentMap()) {
            if (k != dropKey) {
                next.put(k, v);
            }
        }
        if (String key ?= addKey, String value ?= addValue) {
            next.put(key, value);
        }
        return next.makeImmutable();
    }

    /**
     * Store a value under `key`. Setting `Null` removes the key.
     *
     * Skips the copy-on-write derivation when the existing entry already matches the
     * incoming value. Each MDC mutation otherwise costs one fresh `HashMap`-build +
     * `makeImmutable` + `SharedContext.withValue` token; the no-op fast path matters in
     * code paths that reassert request context on every call (a common pattern in
     * cross-cutting middleware that re-`put`s `requestId` defensively). Symmetrical to
     * the existing fast path in [remove].
     */
    void put(String key, String? value) {
        if (value == Null) {
            remove(key);
            return;
        }
        if (String existing := currentMap().get(key), existing == value) {
            return;
        }
        mapContext.withValue(derive(key, key, value));
    }

    /**
     * Read the value for `key`, or `Null` if no value is set on the calling fiber.
     */
    String? get(String key) = currentMap().getOrNull(key);

    /**
     * Remove `key` from the calling fiber's context. No-op if no value is set.
     */
    void remove(String key) {
        if (currentMap().contains(key)) {
            mapContext.withValue(derive(key, Null, Null));
        }
    }

    /**
     * Remove all entries from the calling fiber's context.
     */
    void clear() {
        if (!currentMap().empty) {
            mapContext.withValue([]);
        }
    }

    /**
     * An immutable snapshot of the current fiber's context map. Sinks call this when
     * emitting an event so the captured state is independent of any subsequent mutation.
     * Returns the empty map if no value has been set on the calling fiber.
     */
    @RO Map<String, String> copyOfContextMap.get() = currentMap();
}
