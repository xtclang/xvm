/**
 * Corresponds to `log/slog.Attr` (`go.dev/src/log/slog/attr.go`). The single carrier
 * for structured data attached to a log record.
 *
 * Every piece of structured data — what SLF4J would split across `arguments`, `marker`,
 * and `keyValues` — is expressed as one of these. `Logger.with(...)` asks the handler
 * to derive a new handler with "always include these" attributes; the per-call
 * `info(message, extra)` adds more for that call only.
 *
 *      Attr.of("user", "alice")
 *      Attr.of("count", 42)
 *      Attr.of("audit", True)
 *      Attr.of("err",  e)
 *
 * # Value typing
 *
 * Go's `slog.Value` is a closed discriminated union (`KindString`, `KindInt64`,
 * `KindBool`, `KindDuration`, `KindTime`, `KindAny`, `KindGroup`, `KindLogValuer`).
 * In Ecstasy we accept any `Object` here and let the handler do the case split via
 * `value.is(...)`. This is closer to how the SLF4J library carries `Map<String, Object>`
 * and avoids inventing a parallel kind-tag enum. The trade-off — slightly more work in
 * each handler — is documented in `lib-logging-vs-lib-slogging.md` § 3.5.
 *
 * Values must be `Passable` (`immutable` or a service) because `Attr` is a `const` and
 * its fields are auto-frozen on construction. Trying to put a mutable class instance in
 * here will fail with the standard "not freezable" diagnostic.
 *
 * # Lazy values
 *
 * Use [lazy] for expensive per-record values:
 *
 *      logger.debug("payload", [Attr.lazy("json", () -> serialize(payload))]);
 *
 * The logger resolves lazy values only after [Handler.enabled] accepts the record. This
 * is the Ecstasy POC's equivalent of Go slog values that implement `LogValuer`.
 *
 * # Groups
 *
 * Setting `value` to an `Attr[]` represents a nested group, equivalent to slog's
 * `slog.Group("user", slog.String("id", "u_1"), slog.String("role", "admin"))`. The
 * handler decides how to render groups (JSON nests, text concatenates with dots).
 */
const Attr(String key, Object value) {

    /**
     * Convenience factory. Mirrors slog's `slog.String("k", v)` / `slog.Int(...)` —
     * the per-type Go helpers exist there because Go has no generic / `Object` parent
     * type; in Ecstasy a single `of` is sufficient because everything inherits `Object`.
     */
    static Attr of(String key, Object value) = new Attr(key, value);

    /**
     * Construct a nested group attribute. Equivalent to slog's
     * `slog.Group(name, attrs...)`.
     */
    static Attr group(String name, Attr[] attrs) = new Attr(name, attrs);

    /**
     * Construct an attribute whose value is computed lazily after the level check.
     *
     * Prefer this for expensive per-call data. Context attrs passed to `Logger.with(...)`
     * should normally be stable eager values; if they are lazy, [BoundHandler] resolves
     * them when an enabled record is handled.
     */
    static Attr lazy(String key, ObjectSupplier value) = new Attr(key, new LazyValue(value));

    /**
     * Return this attr with any lazy value resolved. Nested groups are resolved
     * recursively so handlers see ordinary values.
     */
    Attr resolved() {
        if (LazyValue lazy := value.is(LazyValue)) {
            return new Attr(key, lazy.resolve());
        }
        if (Attr[] attrs := value.is(Attr[])) {
            return Attr.group(key, resolveAll(attrs));
        }
        return this;
    }

    /**
     * Resolve lazy values in an attr array, returning an immutable array suitable for
     * a [Record] or handler boundary.
     */
    static Attr[] resolveAll(Attr[] attrs) {
        if (attrs.empty) {
            return [];
        }

        Attr[] resolved = new Array<Attr>(attrs.size);
        for (Attr attr : attrs) {
            resolved.add(attr.resolved());
        }
        return resolved.toArray(Constant);
    }
}
