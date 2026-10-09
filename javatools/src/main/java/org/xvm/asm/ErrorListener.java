package org.xvm.asm;

import java.lang.management.ManagementFactory;

import java.text.MessageFormat;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Consumer;

import org.xvm.compiler.Source;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

import static java.util.Objects.requireNonNull;

import static org.xvm.util.Handy.quotedString;

/**
 * Receives diagnostics from compilation, assembly, and verification, and exposes the state that
 * callers use to decide whether work may continue.
 *
 * <p>Reporting and continuation are separate operations. {@link #log(ErrorInfo)} delivers a
 * diagnostic; {@link #isAbortDesired()} answers the current stop policy. A compiler caller that
 * previously consumed the boolean result of {@code log} must query the policy explicitly:
 * <pre>{@code
 * errors.error(code, ErrorListener.in(source, start, end), arguments);
 * if (errors.isAbortDesired()) {
 *     return;
 * }
 * }</pre>
 * <p>This gives forwarding listeners one place to preserve a parent's error budget or abort
 * request. It also lets a host query a stop policy independently of emitting another diagnostic.
 * Changing {@code log} to {@code void} is an intentional source and binary API change; existing
 * Java implementations and callers must migrate and recompile.
 *
 * <p>Use {@link #collecting(Consumer)} to deliver diagnostics to a host callback while maintaining
 * severity and code state, {@link ErrorList} to retain and deduplicate reports with an error
 * budget, and {@link #tee(ErrorListener, ErrorListener)} to forward to two destinations. A bare
 * lambda implements only reporting and inherits state queries that always return {@code false}.
 *
 * <p>Use {@link #branch(AstNode)} when speculative diagnostics may need to be kept, or
 * {@link #silence(Silence)} when they must be discarded while preserving this listener's abort
 * policy. {@link #silent(Silence)} is a standalone discard sink with no parent policy. The named
 * reasons replace the ambiguous intent of an unnamed discard sink such as {@code BLACKHOLE};
 * they do not select different compiler modes.
 *
 * <p>Keep an operation's original listener reference stable. Pass a derived silence or branch to
 * temporary work instead of replacing the owner with a discard sink. This preserves the destination
 * and its stop policy for later work; it does not make the listener's accumulated state immutable.
 *
 * <p>Listeners belong to an operation. Hosts must serialize callbacks and compiler access;
 * these factories do not make compilation or listener state thread-safe. Reporting callbacks
 * may throw, and the forwarding helpers do not catch or translate those exceptions.
 */
@FunctionalInterface
public interface ErrorListener {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Deliver one diagnostic to this listener.
     *
     * <p>Implementations decide whether to retain, forward, deduplicate, or discard the report.
     * Reporting supplies no continuation result. Call {@link #isAbortDesired()} at the caller's
     * stop checkpoints, and use {@link #hasSeriousErrors()} or {@link #hasError(String)} when deciding
     * whether a compiler result is usable. A custom reporting callback must maintain those queries
     * itself or be wrapped with {@link #collecting(Consumer)}.
     *
     * @param err  the diagnostic to report
     */
    void log(ErrorInfo err);

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param source      the source code (optional)
     * @param lPosStart   the position in the source where the error was detected
     * @param lPosEnd     the position in the source at which the error concluded
     *
     * @deprecated use {@link #log(Severity, String, Site, Object...)} with {@link #in}
     */
    @Deprecated
    default void log(Severity severity, String sCode, Object[] aoParam, Source source, long lPosStart, long lPosEnd) {
        log(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
    }

    /**
     * Handles the logging of an error that originates in an Ecstasy XVM structure.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param xs          the XvmStructure that the error is related to; may
     *                    be null
     *
     * @deprecated use {@link #log(Severity, String, Site, Object...)} with {@link #at}
     */
    @Deprecated
    default void log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
        log(severity, sCode, at(xs), aoParam);
    }

    // ----- reporting -----------------------------------------------------------------------------

    /**
     * Construct and report a diagnostic with an explicit location and trailing message arguments.
     *
     * <p>Use {@link #in(Source, long, long)} for source text, {@link #at(XvmStructure)} for an
     * assembled structure, or {@link #NOWHERE} when no location is available. Packaging the location
     * as a {@link Site} leaves the final parameter available for varargs, so callers need not build
     * an {@code Object[]} between the message code and location arguments.
     *
     * <p>The default implementation creates an {@link ErrorInfo} and delegates to {@link #log(ErrorInfo)}.
     * It does not test the listener's abort policy or stop the caller.
     *
     * @param severity  the diagnostic severity
     * @param sCode     the message code from the compiler's error resources
     * @param site      the diagnostic location; must not be null
     * @param aoParam   the message-format arguments, or an empty array when none are needed
     *
     * @throws NullPointerException if {@code site} is null
     */
    default void log(Severity severity, String sCode, Site site, Object... aoParam) {
        switch (site) {
            case Site.In in -> log(new ErrorInfo(severity, sCode, aoParam, in.source(), in.lPosStart(), in.lPosEnd()));
            case Site.At at -> log(new ErrorInfo(severity, sCode, aoParam, at.xs()));
            case Site.None _ -> log(new ErrorInfo(severity, sCode, aoParam, null, 0, 0));
        }
    }

    /**
     * Report an {@link Severity#ERROR} diagnostic for an operation that failed validation.
     *
     * <p>The listener's error budget determines whether this also requests an abort. Reporting an
     * error does not itself stop the caller.
     *
     * @param sCode    the message code
     * @param site     the diagnostic location; must not be null
     * @param aoParam  the message-format arguments
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void error(String sCode, Site site, Object... aoParam) {
        log(Severity.ERROR, sCode, site, aoParam);
    }

    /**
     * Report a {@link Severity#WARNING} diagnostic without classifying it as a serious error.
     *
     * @param sCode    the message code
     * @param site     the diagnostic location; must not be null
     * @param aoParam  the message-format arguments
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void warn(String sCode, Site site, Object... aoParam) {
        log(Severity.WARNING, sCode, site, aoParam);
    }

    /**
     * Report an {@link Severity#INFO} diagnostic, for example an informational compiler message.
     *
     * @param sCode    the message code
     * @param site     the diagnostic location; must not be null
     * @param aoParam  the message-format arguments
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void info(String sCode, Site site, Object... aoParam) {
        log(Severity.INFO, sCode, site, aoParam);
    }

    /**
     * Report a {@link Severity#FATAL} diagnostic for work that cannot continue successfully.
     *
     * <p>{@link ErrorList} and {@link #collecting(Consumer)} request an abort after a fatal report,
     * regardless of their count policy. This method does not itself throw or transfer control;
     * the caller must return, throw, or honor its next stop checkpoint. Code that cannot safely
     * continue must stop explicitly even when the chosen listener discards the report.
     *
     * @param sCode    the message code
     * @param site     the diagnostic location; must not be null
     * @param aoParam  the message-format arguments
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void fatal(String sCode, Site site, Object... aoParam) {
        log(Severity.FATAL, sCode, site, aoParam);
    }

    /**
     * Create a stateful listener that forwards every report to a host callback.
     *
     * <p>Use this when an embedding host or editor collects diagnostics through a consumer, but the
     * compiler still needs accurate state queries. Before invoking the consumer, the listener records
     * the highest severity and the reported code. {@link #hasSeriousErrors()} becomes true for ERROR
     * or FATAL, {@link #hasError(String)} recognizes codes from any severity, and
     * {@link #isAbortDesired()} becomes true only for FATAL. There is no count budget.
     *
     * <p>Every report is forwarded, including duplicates; use {@link ErrorList} for deduplication or
     * a count budget. If the consumer throws, its exception propagates and the recorded state remains.
     * Create a new listener for each independent operation; there is no reset operation.
     *
     * @param consumer  the non-null destination for each diagnostic, invoked synchronously
     *
     * @return a new listener that retains severity and code state while forwarding reports
     *
     * @throws NullPointerException if {@code consumer} is null
     */
    static ErrorListener collecting(Consumer<ErrorInfo> consumer) {
        requireNonNull(consumer, "consumer");
        return new ErrorListener() {
            @Override
            public void log(ErrorInfo err) {
                Severity severity = err.getSeverity();
                if (severity.isWorseThan(m_severity)) {
                    m_severity = severity;
                }
                f_setCodes.add(err.getCode());
                consumer.accept(err);
            }

            @Override
            public boolean isAbortDesired() {
                return m_severity == Severity.FATAL;
            }

            @Override
            public boolean hasSeriousErrors() {
                return m_severity.isAtLeast(Severity.ERROR);
            }

            @Override
            public boolean hasError(String sCode) {
                return f_setCodes.contains(sCode);
            }

            @Override
            public String toString() {
                return "Collecting(worst=" + m_severity + ")";
            }

            private final Set<String> f_setCodes = ConcurrentHashMap.newKeySet();
            private volatile Severity           m_severity = Severity.NONE;
        };
    }

    /**
     * Create a listener that forwards reports to two destinations in order.
     *
     * <p>For example, combine a host's budgeted listener with a recorder whose reports may later be
     * replayed. Each report goes to {@code first}, then {@code second}; an abort request does not
     * skip delivery to the second listener. If the first listener throws, the exception propagates
     * and the second is not called.
     *
     * <p>The wrapper keeps no state of its own. Its abort, serious-error, and code queries are true
     * when either destination answers true; it is silent only when both are silent. It uses this
     * interface's default buffering {@link #branch(AstNode)} behavior rather than independently
     * branching each destination.
     *
     * @param first   the non-null first destination
     * @param second  the non-null second destination
     *
     * @return a listener combining delivery and the two destinations' current state
     *
     * @throws NullPointerException if either destination is null
     */
    static ErrorListener tee(ErrorListener first, ErrorListener second) {
        requireNonNull(first, "first");
        requireNonNull(second, "second");
        return new ErrorListener() {
            @Override
            public void log(ErrorInfo err) {
                first.log(err);
                second.log(err);
            }

            @Override
            public boolean isAbortDesired() {
                return first.isAbortDesired() || second.isAbortDesired();
            }

            @Override
            public boolean hasSeriousErrors() {
                return first.hasSeriousErrors() || second.hasSeriousErrors();
            }

            @Override
            public boolean hasError(String sCode) {
                return first.hasError(sCode) || second.hasError(sCode);
            }

            @Override
            public boolean isSilent() {
                return first.isSilent() && second.isSilent();
            }

            @Override
            public String toString() {
                return "Tee(" + first + ", " + second + ")";
            }
        };
    }

    /**
     * Describe a diagnostic associated with an assembled structure.
     *
     * <p>This does not calculate a source span. A node-associated diagnostic branch can re-anchor
     * the structure report to its node; otherwise an editor host must interpret the structure site.
     * Prefer {@link #NOWHERE} when the caller has no location.
     *
     * @param xs  the related structure; null is accepted for legacy unpositioned reports
     *
     * @return a structure site referring to {@code xs}
     */
    static Site at(XvmStructure xs) {
        return new Site.At(xs);
    }

    /**
     * Describe a diagnostic over a source span.
     *
     * <p>Positions use the encoding returned by {@link Source#getPosition()}, not byte offsets or
     * LSP positions. This factory stores the supplied values without validating or copying the source.
     *
     * @param source    the source being diagnosed; prefer {@link #NOWHERE} if absent
     * @param lPosStart  the encoded start position, inclusive
     * @param lPosEnd    the encoded end position, exclusive
     *
     * @return a site referring to the supplied source span
     */
    static Site in(Source source, long lPosStart, long lPosEnd) {
        return new Site.In(source, lPosStart, lPosEnd);
    }

    /**
     * The location of a diagnostic, independent of its severity and message arguments.
     *
     * <p>Hosts can switch exhaustively over source spans, structure references, and unpositioned
     * reports. These values describe locations; they do not resolve a structure to source text or
     * detach the referenced compiler objects for concurrent access.
     */
    sealed interface Site {
        /**
         * A source span using the encoded positions returned by {@link Source#getPosition()}.
         *
         * @param source    the referenced source; not copied
         * @param lPosStart  the inclusive encoded start position
         * @param lPosEnd    the exclusive encoded end position
         */
        record In(Source source, long lPosStart, long lPosEnd) implements Site {}

        /**
         * A diagnostic associated with a structure rather than an explicit source span.
         *
         * @param xs  the related structure; null represents a legacy unpositioned report
         */
        record At(XvmStructure xs) implements Site {}

        /**
         * A diagnostic with no source span or structure, such as an operation-wide setup failure.
         */
        record None() implements Site {}
    }

    /**
     * The shared location for a diagnostic with no source span or structure.
     */
    Site NOWHERE = new Site.None();

    /**
     * Create a temporary buffer for diagnostics from work whose reports may later be retained.
     *
     * <p>For speculative validation, pass the branch to the trial operation and call {@link #merge()}
     * once if its reports should reach this listener. Otherwise discard the branch. Reports are
     * buffered and deduplicated in an {@link ErrorList}; they are not forwarded during the trial.
     * Use {@link #silence(Silence)} instead when the reports must never be retained.
     *
     * <p>The default branch has an unlimited count budget, still aborts on its own FATAL, and observes
     * this parent's abort request. {@link ErrorList#branch(AstNode)} instead copies its configured
     * count budget. A supplied node re-anchors structure-site reports to that node's source span;
     * explicit source-site reports retain their own location.
     *
     * @param node  the optional source context for structure-site diagnostics
     *
     * @return a new buffering child listener
     */
    default ErrorListener branch(AstNode node) {
        return new ErrorList.BranchedErrorListener(this, ErrorList.UNLIMITED, node);
    }

    /**
     * Publish a branch's retained diagnostics to its parent and return the parent.
     *
     * <p>The default implementation is a no-op returning this listener, for a sink that has no parent.
     * An {@link ErrorList.BranchedErrorListener} forwards its retained reports without clearing them;
     * merging it again replays them again. A parent {@link ErrorList} can deduplicate that replay,
     * but an arbitrary callback need not. Merge each accepted branch once.
     *
     * @return the parent for a branch, or this listener when there is nothing to merge
     */
    default ErrorListener merge() {
        return this;
    }

    /**
     * The caller's reason for deliberately discarding diagnostics.
     *
     * <p>The legacy {@code BLACKHOLE} name did not distinguish a type-fit probe, follow-on errors
     * from incomplete analysis, and a caller that deliberately wanted no reports. Naming the reason
     * makes that decision reviewable and searchable. These reasons have the same discard behavior;
     * compiler semantics must not depend on which reason was selected.
     *
     * <p>Suppression does not buffer diagnostics for later publication. Use {@link #branch(AstNode)}
     * when deciding whether reports should eventually be kept. Use a derived {@link #silence(Silence)}
     * when the parent listener's stop policy must remain active.
     */
    enum Silence {
        /**
         * Speculative work whose return value answers the question, such as whether an expression fits
         * a candidate type. Its failure is not a diagnostic for the user's program. Use a branch instead
         * if the trial's reports may be needed when all alternatives fail.
         */
        PROBE,

        /**
         * Follow-on diagnostics from information already known to be incomplete. Discard them to avoid
         * burying the original failure in its consequences. Derive this silence from the operation's
         * listener when its abort policy must remain active. Discarded reports cannot later be recovered.
         */
        CASCADE,

        /**
         * An explicit decision not to receive diagnostics at this destination, for example a command-line
         * launcher's external delegate when its console already reports them. This is the general-purpose
         * replacement for an intentional {@code BLACKHOLE}; choose PROBE or CASCADE when those reasons apply.
         */
        DISCARD
    }

    /**
     * Obtain a shared discard sink without a parent listener or inherited stop policy.
     *
     * <p>It retains no diagnostics, reports no serious errors or codes, never requests an abort, and
     * is silent. All reasons behave identically. If the caller already has an operation listener,
     * prefer {@link #silence(Silence)} to preserve that listener's abort or cancellation request.
     *
     * @param why  the non-null reason for discarding reports
     *
     * @return the shared stateless sink for the requested reason
     *
     * @throws NullPointerException if {@code why} is null
     */
    static ErrorListener silent(Silence why) {
        return switch (why) {
            case PROBE   -> SILENT_PROBE;
            case CASCADE -> SILENT_CASCADE;
            case DISCARD -> SILENT_DISCARD;
        };
    }

    /**
     * Create a discard wrapper for part of this listener's operation while retaining its stop policy.
     *
     * <p>The wrapper does not forward, retain, or replay reports, and its serious-error and code
     * queries return false. Its abort query delegates to this listener, so suppressing reports cannot
     * cancel a parent's stop request. {@link SilentErrorListener#suppressed()} exposes that parent,
     * not the discarded diagnostics. The parent itself is not modified.
     *
     * <p>Silencing an existing {@link SilentErrorListener} returns the same instance and keeps its
     * original reason, even when a different reason is requested.
     *
     * @param why  the non-null reason for discarding reports
     *
     * @return a wrapper retaining this listener's abort policy, or the existing silent wrapper
     */
    default ErrorListener silence(Silence why) {
        return new SilentErrorListener(this, why);
    }

    /**
     * Describe why reports are discarded, when the listener exposes a named suppression policy.
     *
     * <p>This is explanatory metadata, not a compiler mode. Wrappers do not necessarily expose a
     * reason even when {@link #isSilent()} is true; use that predicate to test silence.
     *
     * @return the suppression reason, or null when no named reason is exposed
     */
    default Silence silenceReason() {
        return null;
    }

    /**
     * Ask whether the current operation should stop at the caller's next safe checkpoint.
     *
     * <p>This query is independent of reporting and need not imply that an error was emitted. A
     * listener may request a stop because of an error budget, a fatal diagnostic, or host policy.
     * Conversely, serious errors may be collected without exhausting the budget. The caller owns
     * the control flow; the default implementation never requests an abort.
     *
     * @return true if the caller should stop the operation
     */
    default boolean isAbortDesired() {
        return false;
    }

    /**
     * Ask whether this listener has recorded an ERROR or FATAL diagnostic.
     *
     * <p>Use this to decide whether a compiler result is usable, not as a substitute for the abort
     * policy. The default is false because a reporting-only implementation retains no state.
     *
     * @return true if a serious diagnostic has been recorded
     */
    default boolean hasSeriousErrors() {
        return false;
    }

    /**
     * Ask whether this listener has recorded the specified diagnostic code at any severity.
     *
     * <p>The default returns false. Stateful implementations such as {@link ErrorList} and
     * {@link #collecting(Consumer)} record codes for this query.
     *
     * @param sCode  the diagnostic code to look for
     *
     * @return true if the code has been recorded
     */
    default boolean hasError(String sCode) {
        return false;
    }

    /**
     * Ask whether reports sent to this listener are ultimately discarded.
     *
     * <p>Callers can skip work done solely to construct diagnostics when the answer is true. They
     * must still perform semantic work and honor {@link #isAbortDesired()}; a silent wrapper may
     * retain a parent's stop request. The default is false. A buffering branch can be silent when
     * its eventual parent discards the reports, even though the branch records its local state.
     *
     * @return true if the diagnostic destination is silent
     */
    default boolean isSilent() {
        return false;
    }

    // ----- inner class: SilentErrorListener ------------------------------------------------------

    /**
     * A discard listener carrying a reason and, optionally, a parent stop policy.
     *
     * <p>Reports are neither forwarded nor retained. The inherited serious-error and code queries
     * return false; the abort query delegates to the parent when present. {@link #suppressed()}
     * returns that parent only, and cannot recover discarded messages. Prefer the
     * {@link ErrorListener#silent(Silence)} and {@link ErrorListener#silence(Silence)} factories.
     */
    class SilentErrorListener
            implements ErrorListener {
        /**
         * Construct a discard listener with an optional parent abort policy.
         *
         * @param errs  the parent whose abort query is retained, or null for no parent policy
         * @param why   the non-null reason for discarding reports
         *
         * @throws NullPointerException if {@code why} is null
         */
        public SilentErrorListener(ErrorListener errs, Silence why) {
            f_errs = errs;
            f_why  = requireNonNull(why, "why");
        }

        /**
         * Return the listener whose reports this wrapper suppresses.
         *
         * <p>This is a reference to the original destination, not a buffer of discarded diagnostics.
         * The wrapper does not mutate or silence that destination for other callers.
         *
         * @return the parent listener, or null for a standalone discard sink
         */
        public ErrorListener suppressed() {
            return f_errs;
        }

        @Override
        public void log(ErrorInfo err) {
            // discarded on purpose; f_why says which purpose
        }

        @Override
        public ErrorListener merge() {
            return this;
        }

        @Override
        public boolean isAbortDesired() {
            // Suppressing reports does not make cancelled work useful again.
            return f_errs != null && f_errs.isAbortDesired();
        }

        @Override
        public boolean isSilent() {
            return true;
        }

        @Override
        public Silence silenceReason() {
            return f_why;
        }

        @Override
        public ErrorListener silence(Silence why) {
            // already silent for a stated reason; a second reason would only add a layer
            return this;
        }

        @Override
        public String toString() {
            return f_errs == null ? "(" + f_why + ")" : "(" + f_why + " of " + f_errs + ")";
        }

        private final ErrorListener f_errs;
        private final Silence       f_why;
    }

    // ----- inner class: Runtime ErrorListener ----------------------------------------------------

    /**
     * A console sink that remembers an abort request after an ERROR or FATAL report.
     *
     * <p>It prints reports to standard output. Unlike the older implementation, reporting does not
     * deliberately throw to stop the operation; callers must query {@link #isAbortDesired()} and
     * choose their own failure handling. The abort flag remains set for the life of this instance.
     *
     * <p>This sink does not collect diagnostic codes or override the serious-error query. Use
     * {@link ErrorList} or {@link ErrorListener#collecting(Consumer)} for a stateful compiler host.
     */
    class RuntimeErrorListener
            implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            System.out.println(err.getSeverity() + ": " + err);
            if (err.getSeverity().isAtLeast(Severity.ERROR)) {
                m_fAbort = true;
            }
        }

        /**
         * This used to throw from inside log(), which made the decision to stop - and the type of
         * the exception that stopped it - a property of who was listening rather than of what went
         * wrong. A caller that asks gets the same answer; a caller that does not is no longer
         * interrupted in the middle of reporting.
         */
        @Override
        public boolean isAbortDesired() {
            return m_fAbort;
        }

        private volatile boolean m_fAbort;

        @Override
        public String toString() {
            return "(Runtime error listener)";
        }
    }

    // ----- inner class: ErrorInfo ----------------------------------------------------------------

    /**
     * Represents the information logged for a single error.
     */
    class ErrorInfo {
        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING},
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param source      the source code
         * @param lPosStart   the starting position in the source code
         * @param lPosEnd     the ending position in the source code
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd) {
            m_severity   = severity;
            m_sCode      = sCode;
            m_aoParam    = aoParam;
            m_source     = source;
            m_lPosStart  = lPosStart;
            m_lPosEnd    = lPosEnd;
        }

        /**
         * Construct an ErrorInfo object.
         *
         * @param severity    the severity level of the error; one of
         *                    {@link Severity#INFO}, {@link Severity#WARNING},
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param xs          the related structure, or null for an unpositioned diagnostic
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            m_xs       = xs;
            // TODO need to be able to ask the XVM structure for the source & location
        }

        /**
         * Describe this diagnostic's location as a source span, structure, or unpositioned report.
         *
         * <p>A source takes precedence when present. Otherwise a non-null structure yields a structure
         * site; if neither is present, the result is {@link ErrorListener#NOWHERE}. No structure-to-source
         * lookup or copying of compiler objects occurs here.
         *
         * @return the diagnostic location
         */
        public Site site() {
            return m_source != null ? new Site.In(m_source, m_lPosStart, m_lPosEnd)
                 : m_xs     != null ? new Site.At(m_xs)
                                    : NOWHERE;
        }

        /**
         * @return the Severity of the error
         */
        public Severity getSeverity() {
            return m_severity;
        }

        /**
         * @return the error code
         */
        public String getCode() {
            return m_sCode;
        }

        /**
         * @return the error message parameters
         */
        public Object[] getParams() {
            return m_aoParam;
        }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that includes the error code
         */
        public String getMessage() {
            return getCode() + ": " + getMessageText();
        }

        /**
         * Produce a localized message based on the error code and related parameters.
         *
         * @return a formatted message for display that doesn't include the error code
         */
        public String getMessageText() {
            return MessageFormat.format(RESOURCES.getString(getCode()), getParams());
        }

        /**
         * @return the source code
         */
        public Source getSource() {
            return m_source;
        }

        /**
         * @return the line number (zero based) at which the error occurred
         */
        public int getLine() {
            return Source.calculateLine(m_lPosStart);
        }

        /**
         * @return the offset (zero based) at which the error occurred
         */
        public int getOffset() {
            return Source.calculateOffset(m_lPosStart);
        }

        /**
         * @return the line number (zero based) at which the error concluded
         */
        public int getEndLine() {
            return Source.calculateLine(m_lPosEnd);
        }

        /**
         * @return the offset (zero based) at which the error concluded
         */
        public int getEndOffset() {
            return Source.calculateOffset(m_lPosEnd);
        }

        /**
         * @return the XvmStructure that this error is related to, or null
         */
        public XvmStructure getXvmStructure() {
            return m_xs;
        }

        /**
         * @return an ID that allows redundant errors to be filtered out
         */
        public String genUID() {
            StringBuilder sb = new StringBuilder();
            sb.append(m_severity.ordinal())
                    .append(':')
                    .append(m_sCode);

            if (m_aoParam != null) {
                sb.append('#')
                  .append(Arrays.hashCode(m_aoParam));
            }

            if (!m_sCode.startsWith("VERIFY")) {
                if (m_source != null) {
                    sb.append(':')
                      .append(m_source.getFileName())
                      .append(':')
                      .append(m_lPosStart)
                      .append(':')
                      .append(m_lPosStart);
                }
                if (m_xs != null) {
                    sb.append(':')
                      .append(m_xs.getDescription());
                }
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // source code location
            if (m_source != null) {
                String sFile = m_source.getFileName();
                if (sFile != null) {
                    // output file:line as IntelliJ will then link to the line
                    if (INTELLIJ_IDEA) {
                        sb.append(sFile)
                            .append(" (")
                            .append(sFile.substring(sFile.lastIndexOf('/') + 1))
                            .append(':')
                            .append(getLine() + 1)
                            .append(") ");
                    } else {
                        sb.append(sFile)
                            .append(':').append(getLine() + 1)
                            .append(' ');
                    }
                }

                sb.append("[")
                  .append(getLine() + 1)
                  .append(':')
                  .append(getOffset() + 1);

                if (getEndLine() != getLine() || getEndOffset() != getOffset()) {
                    sb.append("..")
                      .append(getEndLine() + 1)
                      .append(':')
                      .append(getEndOffset() + 1);
                }

                sb.append("] ");
            }

            // XVM Structure id
            XvmStructure xs = getXvmStructure();
            while (xs != null) {
                Constant constId = xs.getIdentityConstant();
                if (constId == null) {
                    xs = xs.getContaining();
                } else {
                    sb.append("[")
                      .append(constId)
                      .append("] ");
                    break;
                }
            }

            // localized message
            sb.append(getMessage());

            // source code snippet
            if (m_source != null && m_lPosStart != m_lPosEnd) {
                String sSource = m_source.toString(m_lPosStart, m_lPosEnd);
                if (sSource.length() > 80) {
                    sSource = sSource.substring(0, 77) + "...";
                }

                sb.append(" (")
                  .append(quotedString(sSource))
                  .append(')');
            }

            return sb.toString();
        }

        private final Severity     m_severity;
        private final String       m_sCode;
        private final Object[]     m_aoParam;
        private       Source       m_source;
        private       long         m_lPosStart;
        private       long         m_lPosEnd;
        private       XvmStructure m_xs;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Text of the error messages.
     */
    ResourceBundle RESOURCES = ResourceBundle.getBundle("errors");

    /**
     * Shared standalone sink for speculative probes; prefer {@link #silent(Silence)} at call sites.
     */
    ErrorListener SILENT_PROBE   = new SilentErrorListener(null, Silence.PROBE);
    /**
     * Shared standalone sink for cascade suppression. Use {@link #silence(Silence)} on an operation
     * listener when its abort policy must be preserved.
     */
    ErrorListener SILENT_CASCADE = new SilentErrorListener(null, Silence.CASCADE);
    /**
     * Shared standalone sink for an explicitly unwanted diagnostic destination.
     */
    ErrorListener SILENT_DISCARD = new SilentErrorListener(null, Silence.DISCARD);

    /**
     * Shared console fallback. Its abort flag is sticky across uses; it is not a fresh per-operation
     * collector. Embedding hosts should supply their own listener.
     */
    ErrorListener RUNTIME = new RuntimeErrorListener();

    /**
     * Legacy compiler/tool error-budget constant. This is distinct from
     * {@link ErrorList#DEFAULT_MAX_ERRORS}, which configures the no-argument {@link ErrorList}
     * constructor. Prefer an explicit budget when a caller requires a particular limit.
     */
    int DEFAULT_MAX_ERRORS = 512;

    /**
     * Indicates that the compiler probably runs inside of IntelliJ IDEA.
     */
    boolean INTELLIJ_IDEA = ManagementFactory.getRuntimeMXBean().
                            getInputArguments().toString().contains("IntelliJ");
}
