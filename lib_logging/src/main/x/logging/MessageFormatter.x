/**
 * Corresponds to `org.slf4j.helpers.MessageFormatter` — the SLF4J helper that turns a
 * pattern + arguments into a substituted string and, by SLF4J convention, promotes a
 * trailing `Throwable` argument into a separate cause.
 *
 * `{}`-placeholder substitution, contract-compatible with SLF4J's `MessageFormatter`.
 *
 * Rules, copied from SLF4J for behavioural parity:
 *   - Each unescaped `{}` is replaced by the next argument's `toString()`.
 *   - Excess arguments beyond the placeholder count are ignored (subject to throwable
 *     promotion below).
 *   - Excess placeholders beyond the argument count are left as-is in the output.
 *   - A literal `{}` in the message is escaped as `\{}` — the backslash is consumed and
 *     the `{}` is emitted unchanged.
 *   - A literal backslash before an unrelated `{}` is escaped as `\\{}` — both
 *     backslashes are consumed (one literal output) and the placeholder *is* substituted.
 *   - Throwable promotion: if the last argument is an `Exception` and no placeholder
 *     consumes it, it is returned as the promoted cause rather than appended to the
 *     message. This matches SLF4J's behaviour and is intentionally distinct from the
 *     explicit `cause=` parameter on the per-level methods, which always wins over a
 *     promoted-from-args throwable (`BasicLogger.emit` enforces that ordering).
 */
static service MessageFormatter {

    private static Char DELIM_START   = '{';
    private static Char DELIM_STOP    = '}';
    private static Char ESCAPE_CHAR   = '\\';

    /**
     * Substitute placeholders in `message` with `args`. Returns the formatted message and
     * the promoted-throwable (if any).
     */
    (String formatted, Exception? cause) format(String message, Object[] args) {
        // Determine throwable promotion: if the trailing argument is an Exception, treat it
        // as the candidate cause. The actual decision (promote vs consume as placeholder
        // value) is made below based on placeholder consumption.
        Int        argCount       = args.size;
        Boolean    hasTrailingExn = argCount > 0 && args[argCount - 1].is(Exception);
        Int        argLimit       = hasTrailingExn ? argCount - 1 : argCount;
        Exception? promoted       = Null;

        StringBuffer buf  = new StringBuffer(message.size + 32);
        Int          argI = 0;
        Int          i    = 0;
        Int          n    = message.size;

        while (i < n) {
            Char c = message[i];

            if (c != DELIM_START) {
                buf.append(c);
                ++i;
                continue;
            }

            // saw '{' — peek for '}'
            if (i + 1 >= n || message[i + 1] != DELIM_STOP) {
                buf.append(c);
                ++i;
                continue;
            }

            // we have a `{}` at position i..i+1; check for escapes immediately before
            Boolean escapedDelim    = False;  // \{}  — literal {}
            Boolean doubledEscape   = False;  // \\{} — literal \, then real placeholder

            if (i >= 1 && message[i - 1] == ESCAPE_CHAR) {
                if (i >= 2 && message[i - 2] == ESCAPE_CHAR) {
                    doubledEscape = True;
                } else {
                    escapedDelim = True;
                }
            }

            if (escapedDelim) {
                // remove the escape char we already appended, then emit literal "{}"
                buf.truncate(buf.size - 1);
                buf.append(DELIM_START);
                buf.append(DELIM_STOP);
                i += 2;
                continue;
            }

            if (doubledEscape) {
                // we already appended both backslashes; remove the trailing one (the second
                // one) so the rendered text has a single literal '\' before the (real)
                // substituted argument.
                buf.truncate(buf.size - 1);
            }

            // real placeholder — substitute or leave literal if we've run out of args
            if (argI < argLimit) {
                buf.append(safeToString(args[argI]));
                ++argI;
            } else {
                buf.append(DELIM_START);
                buf.append(DELIM_STOP);
            }
            i += 2;
        }

        // Throwable promotion: the trailing exception is promoted only if no placeholder
        // would have consumed it. argI counted only argLimit, so the trailing Exception is
        // never substituted in here; it's always either promoted or dropped.
        if (hasTrailingExn) {
            promoted = args[argCount - 1].as(Exception);
        }

        return (buf.toString(), promoted);
    }

    /**
     * Defensive `toString` that catches exceptions from the argument's own `toString()` —
     * matches SLF4J's policy of never letting a malformed argument tear down the whole log
     * statement.
     */
    private static String safeToString(Object o) {
        try {
            return o.toString();
        } catch (Exception e) {
            return "[FAILED toString(): " + e.text + "]";
        }
    }
}
