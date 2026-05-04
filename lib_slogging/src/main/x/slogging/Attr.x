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
}
