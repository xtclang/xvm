/**
 * Internal carrier for lazily-computed positional arguments and key/value pairs.
 *
 * The fluent builder ([LoggingEventBuilder]) needs to distinguish "this slot holds an
 * already-computed value" from "this slot holds a function to invoke after the level
 * check passes." Storing the supplier function directly in an `Object[]` does not work
 * by itself, because in Ecstasy `function Object()` is itself an `Object`, so a runtime
 * `value.is(...)` check cannot reliably tell a real `Object` value apart from a supplier
 * the caller wanted invoked. Wrapping the supplier in this dedicated type makes the
 * discrimination explicit: arguments and key/value pairs are stored as `Object[]` /
 * `Map<String, Object>`; the resolver step in [BasicEventBuilder] does
 * `value.is(LazyValue) ? lv.resolve() : value`.
 *
 * # Why this is a `class`, not a `const`
 *
 * The slog-shaped sibling library has the same wrapper at `slogging/LazyValue.x` and it
 * *is* a `const` there, because slog's `Attr` is a `const` and so its `value` field must
 * be `Passable`. Here the wrapper lives only in `BasicEventBuilder` (a `class`) and is
 * resolved into a plain `Object` before crossing any service boundary, so the
 * passability constraint does not apply. Crucially, making this a `class` means the
 * captured supplier closure is NOT auto-frozen on construction — so a caller can write
 *
 *      @Volatile Int calls = 0;
 *      builder.addLazyArgument(() -> { ++calls; return value; }).log("...");
 *
 * and the `++calls` mutation works as expected. A `const` wrapper would freeze the
 * closure environment and `++calls` would throw `ReadOnly` (which is exactly what the
 * slog side documents as a constraint on `Attr.lazy` callers).
 */
class LazyValue(ObjectSupplier supplier) {

    /**
     * Evaluate the deferred value.
     */
    Object resolve() = supplier();
}
