import ecstasy.SharedContext;

/**
 * Optional context helper for code that wants Go-slog-style request propagation without
 * passing a logger through every method signature.
 *
 * Go slog accepts `context.Context` on `LogAttrs` and `Handler.Enabled` / `Handle`.
 * Ecstasy does not need to copy that exact API to be familiar: [SharedContext] already
 * models a logical execution context that flows across service calls and child fibers.
 * `LoggerContext` is the smallest explicit bridge:
 *
 *      Logger requestLog = logger.with([Attr.of("requestId", id)]);
 *      using (loggerContext.bind(requestLog)) {
 *          worker.process^();
 *      }
 *
 *      if (Logger log := loggerContext.current()) {
 *          log.info("inside worker");
 *      }
 *
 * This helper is intentionally separate from [Logger]. The recommended default remains
 * explicit logger passing; `LoggerContext` is available for frameworks, request
 * dispatchers, and adapter layers where implicit context is the least noisy API.
 */
const LoggerContext {

    /**
     * The fiber-local backing store. No default value is supplied, so [current] is
     * conditional and callers must decide their fallback policy explicitly.
     */
    private static SharedContext<Logger> context = new SharedContext("slogging.Logger");

    /**
     * Bind `logger` to the current logical execution context.
     *
     * Use the returned token with `using` so the previous logger is restored reliably.
     */
    SharedContext<Logger>.Token bind(Logger logger) {
        return context.withValue(logger);
    }

    /**
     * Return the context logger when one is bound.
     */
    conditional Logger current() = context.hasValue();

    /**
     * Return the context logger, or `fallback` when no context logger is bound.
     */
    Logger currentOr(Logger fallback) {
        return context.hasValue() ?: fallback;
    }
}
