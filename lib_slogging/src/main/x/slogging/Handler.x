/**
 * Corresponds to `log/slog.Handler` (`go.dev/src/log/slog/handler.go`). The SPI a
 * logging backend implements — analogous to the SLF4J-shaped library's
 * [logging.LogSink], but with a richer surface that lets the handler pre-resolve
 * derivations.
 *
 * # The contract
 *
 *      Boolean enabled(Level level)        — cheap fast-path filter
 *      void    handle(Record record)       — emit the record
 *      Handler withAttrs(Attr[] attrs)     — derive a pre-bound handler
 *      Handler withGroup(String name)      — derive a name-prefixed handler
 *
 * The two extra methods exist so that when user code does `logger.with(attrs)` once and
 * then logs millions of records through the derived logger, the handler can do the
 * "merge attrs into the namespace" work *once* at derivation time rather than on every
 * `handle` call.
 *
 * Handlers that do not maintain their own derived representation can return
 * [BoundHandler] from both derivation methods. Handlers with a native prefix/cache can
 * return their own implementation instead. A true discard handler such as [NopHandler]
 * can still return `this`.
 *
 * # Choosing between `const` and `service` for an implementation
 *
 * Same rule as `lib_logging`'s [logging.LogSink]:
 *
 *   - `const`   — for stateless forwarders / pure adapters. Examples: `TextHandler`,
 *                 `NopHandler`, `JSONHandler`. Construction-time configuration only.
 *   - `service` — for handlers that carry mutable state shared across fibers. Examples:
 *                 `MemoryHandler` (collects records), a future `FileHandler` (owns a
 *                 writer), a future `AsyncHandler` (owns a worker queue).
 *
 * `Logger` is a `const` and references its `handler` through this interface, so every
 * concrete `Handler` must be `Passable` (`immutable` or a `service`). The full rule and
 * the citations to `lib_xunit_engine` / `platform/common` are in `design.md` ("Sink
 * type: `const` vs `service`") — same rule, both libraries.
 */
interface Handler {

    /**
     * Cheap level check. Should be safe to call on every emission.
     */
    Boolean enabled(Level level);

    /**
     * Emit the supplied record.
     *
     * Must not throw under normal conditions; handlers that experience an internal
     * failure (disk full, remote endpoint down) are expected to degrade gracefully —
     * typically by writing to standard error — rather than propagate.
     */
    void handle(Record record);

    /**
     * Return a handler that carries the supplied attributes pre-bound. Use
     * [BoundHandler] for the default semantics, or return a handler-specific derived
     * instance that caches a rendered prefix.
     */
    Handler withAttrs(Attr[] attrs);

    /**
     * Return a handler that namespaces subsequent attributes under the supplied group
     * name. Use [BoundHandler] for the default semantics.
     */
    Handler withGroup(String name);
}
