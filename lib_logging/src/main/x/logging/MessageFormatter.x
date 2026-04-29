/**
 * Corresponds to `org.slf4j.helpers.MessageFormatter` — the SLF4J helper that turns a
 * pattern + arguments into a substituted string and, by SLF4J convention, promotes a
 * trailing `Throwable` argument into a separate cause.
 *
 * `{}`-placeholder substitution, contract-compatible with SLF4J's `MessageFormatter`.
 *
 * Rules, copied from SLF4J for behavioural parity:
 *   - Each unescaped `{}` is replaced by the next argument's `toString()`.
 *   - Excess arguments beyond the placeholder count are ignored.
 *   - Excess placeholders beyond the argument count are left as-is in the output.
 *   - A literal `{}` in the message is escaped as `\{}`.
 *   - A literal backslash before an unrelated `{}` is escaped as `\\{}`.
 *   - The last argument, if it is an `Exception` and there is no remaining placeholder, is
 *     interpreted as a `cause` rather than as a substitution. This mirrors SLF4J's
 *     "throwable promotion" rule.
 *
 * The result of `format` is a `(String, Exception?)` tuple so callers can route the exception
 * separately from the message text.
 */
static service MessageFormatter {

    /**
     * Substitute placeholders in `message` with `args`. Returns the formatted message and
     * the promoted-throwable (if any).
     */
    (String formatted, Exception? cause) format(String message, Object[] args) {
        // TODO(impl): port the SLF4J state machine. The skeleton here unblocks compilation
        //             and other modules that depend on this contract.
        return (message, Null);
    }
}
