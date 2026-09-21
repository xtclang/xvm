package org.xvm.asm;

import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;

import java.text.MessageFormat;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.xvm.compiler.Source;

import org.xvm.compiler.ast.AstNode;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.quotedString;

/**
 * A listener for errors being reported about source code, compilation, assembly, or verification of
 * XVM structures.
 */
@FunctionalInterface
public interface ErrorListener {
    // ----- API -----------------------------------------------------------------------------------

    /**
     * Handles the logging of an error that originates in Ecstasy source code.
     *
     * Recording a diagnostic says nothing about whether the work should continue; ask
     * {@link #isAbortDesired()} for that. The two used to be one boolean, which left a listener
     * that only wants to watch with no correct value to return - false suppressed a legitimate
     * abort and true invented one - and made whether the compiler kept going a property of who was
     * listening.
     *
     * @param err  the error info
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
     * @deprecated use {@link #log(Severity, String, Site, Object...)}, or the severity-named
     *             {@link #error}, {@link #warn}, {@link #info} and {@link #fatal}, and pass the
     *             location as {@link #in in(source, lPosStart, lPosEnd)}
     */
    @Deprecated
    default void log(Severity severity, String sCode, Object[] aoParam, Source source, long lPosStart, long lPosEnd) {
        log(new ErrorInfo(severity, sCode, aoParam, source, lPosStart, lPosEnd));
    }

    /**
     * Handles the logging of an error that originates in an Ecstasy XVM structure.
     *
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING,
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     * @param xs          the XvmStructure that the error is related to; may
     *                    be null
     *
     * @deprecated use {@link #log(Severity, String, Site, Object...)}, or the severity-named
     *             {@link #error}, {@link #warn}, {@link #info} and {@link #fatal}, and pass the
     *             location as {@link #at at(xs)}
     */
    @Deprecated
    default void log(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
        log(new ErrorInfo(severity, sCode, aoParam, xs));
    }

    // ----- reporting -----------------------------------------------------------------------------

    /**
     * Report a diagnostic.
     *
     * The message parameters are the trailing arguments, which is why the location is a
     * {@link Site} rather than the two shapes it takes: an {@code Object[]} in the middle of the
     * signature is what the older overloads need in order to leave room for the location after it,
     * and it is why call sites have to write {@code new Object[]&#123;...&#125;} by hand.
     *
     * @param severity  the severity of the diagnostic
     * @param sCode     the error code that identifies the message
     * @param site      where the diagnostic belongs; see {@link #at} and {@link #in}
     * @param aoParam   the message parameters
     */
    default void log(Severity severity, String sCode, Site site, Object... aoParam) {
        switch (site) {
            case Site.In in -> log(new ErrorInfo(severity, sCode, aoParam, in.source(), in.lPosStart(), in.lPosEnd()));
            case Site.At at -> log(new ErrorInfo(severity, sCode, aoParam, at.xs()));
            case Site.None _ -> log(new ErrorInfo(severity, sCode, aoParam, null, 0, 0));
        }
    }

    /**
     * Report that the caller's operation failed.
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void error(String sCode, Site site, Object... aoParam) {
        log(Severity.ERROR, sCode, site, aoParam);
    }

    /**
     * Report something the compiler recovered from.
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void warn(String sCode, Site site, Object... aoParam) {
        log(Severity.WARNING, sCode, site, aoParam);
    }

    /**
     * Report something that is only being traced.
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void info(String sCode, Site site, Object... aoParam) {
        log(Severity.INFO, sCode, site, aoParam);
    }

    /**
     * Report that the caller cannot continue. Throw on the next line; a diagnostic is a report,
     * not a control-flow mechanism, and nothing should depend on what the listener does with it.
     *
     * @see #log(Severity, String, Site, Object...)
     */
    default void fatal(String sCode, Site site, Object... aoParam) {
        log(Severity.FATAL, sCode, site, aoParam);
    }

    /**
     * A listener that hands each diagnostic to the given consumer, and answers the questions the
     * compiler asks about what it has seen.
     *
     * This is how a host should build one. {@link ErrorListener} is a functional interface, so a
     * bare lambda compiles - but a lambda only supplies {@link #log}, and inherits defaults for
     * {@link #hasSeriousErrors()}, {@link #isAbortDesired()} and {@link #hasError} that answer as
     * though nothing had been reported. The compiler asks those questions around a hundred times
     * during a compilation, to decide whether a stage may proceed, so a host that lambdas the
     * interface directly is telling the compiler that its own diagnostics did not happen.
     *
     * @param consumer  receives each diagnostic as it is reported
     *
     * @return a listener that reports to the consumer and remembers what it reported
     */
    static ErrorListener collecting(Consumer<ErrorInfo> consumer) {
        requireNonNull(consumer, "consumer");
        return new ErrorListener() {
            @Override
            public void log(ErrorInfo err) {
                Severity severity = err.getSeverity();
                if (severity.ordinal() > m_severity.ordinal()) {
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
                return m_severity.compareTo(Severity.ERROR) >= 0;
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
     * Obtain a listener that also abandons the work when someone outside asks it to.
     *
     * The compiler asks {@link #isAbortDesired} at around twenty points - in the lexer, the
     * parser, each pass of the stage manager, and statement validation - so that a spent error
     * budget or a FATAL stops the work rather than letting it run to the end. That is the same
     * question a host needs answered when the work has become pointless for a reason the compiler
     * cannot see: an editor whose user has typed again, so the document being analysed is two
     * keystrokes stale, or a request the client has cancelled.
     *
     * Everything else is the wrapped listener's: what it is told, what it has seen, whether it is
     * silent. Only the decision to stop is shared.
     *
     * @param errs       the listener to wrap
     * @param cancelled  asked whenever the compiler asks whether to stop; it is polled rather
     *                   than pushed, so it must be cheap and safe to call from the compiling
     *                   thread
     *
     * @return a listener that abandons the work when either the wrapped listener or the caller
     *         says so
     */
    static ErrorListener cancellable(ErrorListener errs, BooleanSupplier cancelled) {
        requireNonNull(errs, "errs");
        requireNonNull(cancelled, "cancelled");
        return new ErrorListener() {
            @Override
            public void log(ErrorInfo err) {
                errs.log(err);
            }

            @Override
            public boolean isAbortDesired() {
                return cancelled.getAsBoolean() || errs.isAbortDesired();
            }

            @Override
            public boolean hasSeriousErrors() {
                return errs.hasSeriousErrors();
            }

            @Override
            public boolean hasError(String sCode) {
                return errs.hasError(sCode);
            }

            @Override
            public boolean isSilent() {
                return errs.isSilent();
            }

            @Override
            public ErrorListener branch(AstNode node) {
                // a branch of a cancellable listener is still cancellable: work inside it is just
                // as pointless once the answer is not wanted
                return cancellable(errs.branch(node), cancelled);
            }

            @Override
            public ErrorListener merge() {
                // and it has to be mergeable, or branching it would quietly throw the branch away:
                // the default merge() answers "this", which for a wrapper means the wrapped
                // branch is never merged into what it branched from
                return cancellable(errs.merge(), cancelled);
            }

            @Override
            public Silence silenceReason() {
                return errs.silenceReason();
            }

            @Override
            public String toString() {
                return "Cancellable(" + errs + ")";
            }
        };
    }

    /**
     * Report to both listeners.
     *
     * The abort question is answered by either: a caller that wrapped a budgeted listener has to
     * keep getting the stop it asked for, whatever else is also listening.
     *
     * @param first   one listener
     * @param second  the other
     *
     * @return a listener that reports to both
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
     * @param xs  the structure a diagnostic is about
     *
     * @return the site of a diagnostic about an XVM structure
     */
    static Site at(XvmStructure xs) {
        return new Site.At(xs);
    }

    /**
     * @param source     the source being compiled
     * @param lPosStart  where the diagnostic starts
     * @param lPosEnd    where it ends
     *
     * @return the site of a diagnostic about a span of source
     */
    static Site in(Source source, long lPosStart, long lPosEnd) {
        return new Site.In(source, lPosStart, lPosEnd);
    }

    /**
     * Where a diagnostic came from.
     *
     * Only meaningful once more than one thing reports at a time - parallel compilation, or a
     * resident server serving several requests - where it answers "whose diagnostic is this".
     *
     * Never part of the deduplication key: two reports of the same problem from two threads are
     * one problem, and keying on the thread would turn every duplicate into a distinct diagnostic.
     *
     * @param thread  the name of the thread that reported it
     */
    record Origin(String thread) {
        /**
         * @return the origin of a diagnostic reported now, on this thread
         */
        static Origin here() {
            return new Origin(Thread.currentThread().getName());
        }
    }

    /**
     * Where a diagnostic belongs.
     *
     * A listener receives the location as one of a small closed set of shapes, so a host that
     * republishes diagnostics - an LSP server turning them into editor squiggles, say - can switch
     * over them exhaustively instead of testing which of several nullable fields was populated.
     */
    sealed interface Site {
        /**
         * A diagnostic about a span of source.
         */
        record In(Source source, long lPosStart, long lPosEnd) implements Site {}

        /**
         * A diagnostic about an XVM structure, whose location is wherever that structure is.
         */
        record At(XvmStructure xs) implements Site {}

        /**
         * A diagnostic with no location.
         */
        record None() implements Site {}
    }

    /**
     * The site of a diagnostic that is not about any particular place.
     */
    Site NOWHERE = new Site.None();

    /**
     * Branch this ErrorListener by creating a new one that will collect subsequent errors
     * in the same manner as this one until it is {@link #merge() merged} or discarded in the
     * (optional) context of the specified node.
     *
     * A branch buffers errors that may or may not end up being reported, so deciding to abandon
     * the work is the parent's call and not the branch's: it is given {@link ErrorList#UNLIMITED}
     * rather than a budget of its own. {@link ErrorList} overrides this to pass on the budget it
     * was built with, and the two have to agree, or the compiler would do less work for a host
     * that supplied its own listener than for one that used an ErrorList.
     *
     * @param node  (optional) the context ast node
     *
     * @return the branched-out ErrorListener
     */
    default ErrorListener branch(AstNode node) {
        return new ErrorList.BranchedErrorListener(this, ErrorList.UNLIMITED, node);
    }

    /**
     * Merge all errors collected by this ErrorListener into the one it was branched out of.
     *
     * A listener that was never branched has nothing to merge and is already the sink the errors
     * would be merged into, so merging it is a no-op. It is not an error: whether a listener in
     * hand is a branch is not something its holder should have to know.
     *
     * @return the ErrorListener this one was {@link #branch branched out} of, or this one if it
     *         was not branched out of anything
     */
    default ErrorListener merge() {
        return this;
    }

    /**
     * Why a stretch of work is not reporting its diagnostics.
     *
     * The three behave identically - nothing may branch on which one a listener holds - but they
     * are different intentions, and saying which is meant is the point. Grepping for one of these
     * is the list of the places that meant it.
     */
    enum Silence {
        /**
         * Speculative work whose failure is the answer, and whose failure must therefore not be
         * audible. The compiler constantly asks "would this expression fit that type?", and the
         * return value - not a diagnostic - is what the caller acts on.
         *
         * Not for work whose failure the user should hear about if every alternative also fails;
         * that is {@link ErrorListener#branch}.
         */
        PROBE,

        /**
         * The remainder of a computation already known to be incomplete. A result built from
         * incomplete information produces diagnostics that describe the incompleteness rather
         * than the user's code, and reporting them buries the one diagnostic that matters under
         * a cascade of consequences. Unlike a probe, these are real diagnostics - a host may want
         * them as related information for the diagnostic that does matter, which is why the
         * listener they were taken from stays reachable.
         */
        CASCADE,

        /**
         * The caller has no sink to attach and wants none. Naming it is what keeps "I do not want
         * these" from looking like "I did not think about these".
         */
        DISCARD
    }

    /**
     * Obtain a listener that discards everything, for a caller with none of its own to derive
     * from.
     *
     * @param why  why the diagnostics are being discarded
     *
     * @return a listener that discards what it is given
     */
    static ErrorListener silent(Silence why) {
        return switch (why) {
            case PROBE   -> SILENT_PROBE;
            case CASCADE -> SILENT_CASCADE;
            case DISCARD -> SILENT_DISCARD;
        };
    }

    /**
     * Obtain a listener that discards the rest of what this one would have been told.
     *
     * The result wraps this listener rather than being a shared constant, so the silence is a
     * decision about one stretch of work instead of an anonymous gap, and
     * {@link SilentErrorListener#suppressed} can still reach what was silenced.
     *
     * Applying it twice is the same silence, so it is safe to call per use rather than having to
     * hold the result.
     *
     * @param why  why the diagnostics are being discarded
     *
     * @return a listener that discards what the rest of this work has to say
     */
    default ErrorListener silence(Silence why) {
        return new SilentErrorListener(this, why);
    }

    /**
     * @return why this listener is silent, or null if it is not
     */
    default Silence silenceReason() {
        return null;
    }

    /**
     * @return true if the ErrorListener has decided to abort the process that reported the error
     */
    default boolean isAbortDesired() {
        return false;
    }

    /**
     * @return true iff an error has been logged with at least the Severity of Error
     */
    default boolean hasSeriousErrors() {
        return false;
    }

    /**
     * @return true iff an error has been logged with the specified code
     */
    default boolean hasError(String sCode) {
        return false;
    }

    /**
     * Whether this listener discards what it is told, for one of the reasons {@link Silence}
     * names. {@link #silenceReason} says which.
     *
     * Not debugging-only, despite what this said for a long time: work that exists solely to
     * produce a diagnostic is worth skipping when nobody is listening, and
     * {@code MethodDeclarationStatement} and {@code PropertyDeclarationStatement} both ask before
     * doing it. That is the one thing a caller may branch on - never on <em>which</em> silence it
     * is, which is why the three behave identically.
     *
     * @return true iff this listener discards what it is told
     */
    default boolean isSilent() {
        return false;
    }

    // ----- inner class: SilentErrorListener ------------------------------------------------------

    /**
     * A listener that discards everything, for one of the reasons {@link Silence} names.
     *
     * The three reasons behave identically and deliberately so - nothing may branch on which one
     * it holds - but they are not the same intention, and a host that republishes diagnostics may
     * want to treat them differently. Where the silence was derived from a real listener,
     * {@link #suppressed} still reaches it.
     */
    class SilentErrorListener
            implements ErrorListener {
        /**
         * @param errs  the listener being silenced, or null where the caller had none
         * @param why   why the diagnostics are being discarded
         */
        public SilentErrorListener(ErrorListener errs, Silence why) {
            f_errs = errs;
            f_why  = requireNonNull(why, "why");
        }

        /**
         * @return the listener this silence was derived from, or null if it was not derived from
         *         one - a caller that never had a listener has nothing to suppress
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
     * A simple implementation of the ErrorListener that can be used at runtime. Errors will throw,
     * and non-errors will go to standard out.
     */
    class RuntimeErrorListener
            implements ErrorListener {
        @Override
        public void log(ErrorInfo err) {
            System.out.println(err.getSeverity() + ": " + err);
            if (err.getSeverity().ordinal() >= Severity.ERROR.ordinal()) {
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
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param source      the source code
         * @param lPosStart   the starting position in the source code
         * @param lPosEnd     the ending position in the source code
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam,
                Source source, long lPosStart, long lPosEnd) {
            m_origin     = Origin.here();
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
         *                    {@link Severity#INFO}, {@link Severity#WARNING,
         *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
         * @param sCode       the error code that identifies the error message
         * @param aoParam     the parameters for the error message; may be null
         * @param xs
         */
        public ErrorInfo(Severity severity, String sCode, Object[] aoParam, XvmStructure xs) {
            m_origin   = Origin.here();
            m_severity = severity;
            m_sCode    = sCode;
            m_aoParam  = aoParam;
            m_xs       = xs;
            // TODO need to be able to ask the XVM structure for the source & location
        }

        /**
         * The location of this diagnostic, as a closed set of shapes rather than as several
         * fields of which some are null. A host that republishes diagnostics can switch over the
         * result exhaustively.
         *
         * @return where this diagnostic belongs
         */
        public Site site() {
            return m_source != null ? new Site.In(m_source, m_lPosStart, m_lPosEnd)
                 : m_xs     != null ? new Site.At(m_xs)
                                    : NOWHERE;
        }

        /**
         * @return where this diagnostic was reported from
         */
        public Origin origin() {
            return m_origin;
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
                // the parameters are part of the identity, so they have to be compared, not
                // digested: a 32-bit hash collides, and a collision here does not merge two
                // reports of the same thing, it discards one of two different things
                sb.append('#')
                  .append(Arrays.toString(m_aoParam));
            }

            if (!m_sCode.startsWith("VERIFY")) {
                if (m_source != null) {
                    sb.append(':')
                      .append(m_source.getFileName())
                      .append(':')
                      .append(m_lPosStart)
                      .append(':')
                      .append(m_lPosEnd);
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
        private final Origin       m_origin;
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
     * The shared silences, one per {@link Silence}, for callers with no listener to derive from.
     * Reached through {@link #silent}, which is what call sites name.
     */
    ErrorListener SILENT_PROBE   = new SilentErrorListener(null, Silence.PROBE);
    ErrorListener SILENT_CASCADE = new SilentErrorListener(null, Silence.CASCADE);
    ErrorListener SILENT_DISCARD = new SilentErrorListener(null, Silence.DISCARD);

    ErrorListener RUNTIME = new RuntimeErrorListener();

    /**
     * Indicates that the compiler probably runs inside of IntelliJ IDEA.
     */
    boolean INTELLIJ_IDEA = ManagementFactory.getRuntimeMXBean().
                            getInputArguments().toString().contains("IntelliJ");
}
