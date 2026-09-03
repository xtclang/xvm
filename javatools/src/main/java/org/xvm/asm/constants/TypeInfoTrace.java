package org.xvm.asm.constants;

import java.util.concurrent.atomic.AtomicLong;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

/**
 * Opt-in trace of {@code TypeInfo} construction, tagged with the THREAD, the POOL, and the
 * LISTENER it was reported through.
 *
 * <p>Building a {@code TypeInfo} is recursive, memoized, and - once a pool is shared by concurrent
 * builders - interleaved across threads. That combination makes the usual debugging move, reading
 * a stack trace, nearly useless: the stack says where a build is, not who else is inside the same
 * type, which pool the type belongs to, or which thread published the answer just read. Every
 * concurrency defect found in this machinery so far has been a question of WHO
 * ({@code markBuildingTypeInfo}) or WHICH POOL ({@code f_tlolistDeferred}), so the trace records
 * exactly those two on every event.
 *
 * <p><b>It reports through the {@link ErrorListener}, and that is the point.</b> Emitting to
 * {@code System.err} would answer "what happened"; reporting through the listener also answers
 * <i>whether the listener is threaded all the way through</i>, which is the open question about
 * this machinery. Read the {@code errs=} field of a trace line:
 *
 * <ul>
 * <li>the compile's own {@code ErrorList} - properly plumbed; a diagnostic found here would reach
 *     the caller who asked;</li>
 * <li>{@code SILENT} - somebody passed {@link ErrorListener#BLACKHOLE}. Legitimate for the
 *     "compute" half of {@code ensureTypeInfo} and a defect for the "validate" half, and the trace
 *     is what tells the two apart at a real call site;</li>
 * <li>a listener belonging to something other than this request - for instance a shared library
 *     pool's own sink - meaning a diagnostic discovered while building this type would be
 *     attributed to the wrong owner.</li>
 * </ul>
 *
 * <p>Off unless asked for, and free when off: {@link #ENABLED} is a {@code static final boolean}
 * read once from a system property, so the JIT removes guarded call sites entirely. Enable with
 *
 * <pre>
 *   -Dxvm.typeinfo.trace=all          # everything (very large)
 *   -Dxvm.typeinfo.trace=Directory    # only types whose rendering contains "Directory"
 * </pre>
 *
 * <p>Events are logged at {@link Severity#INFO} under the code {@code TRACE-TI}, so they travel
 * with the diagnostics they are explaining and appear in the right order relative to them. The
 * leading number is a global sequence, so an interleaving can be reconstructed across threads even
 * though lines arrive out of order.
 */
public final class TypeInfoTrace {
    private TypeInfoTrace() {}

    private static final String  FILTER  = System.getProperty("xvm.typeinfo.trace");
    /** True iff tracing was requested; a {@code static final} so guarded call sites cost nothing. */
    public static final  boolean ENABLED = FILTER != null && !FILTER.isEmpty();
    private static final boolean ALL     = ENABLED && ("all".equals(FILTER) || "true".equals(FILTER));

    /** Mirror every event to stderr as well, to measure what the listener chain drops. */
    private static final boolean ECHO = Boolean.getBoolean("xvm.typeinfo.trace.echo");

    private static final AtomicLong SEQ = new AtomicLong();

    /**
     * @param type  the type an event is about (may be null)
     *
     * @return true iff events for this type should be emitted
     */
    public static boolean traces(TypeConstant type) {
        return ENABLED && (ALL || type != null && String.valueOf(type).contains(FILTER));
    }

    /**
     * Emit one trace event through the listener that the traced operation was given.
     *
     * <p>Call sites must guard with {@link #ENABLED} so the argument expressions - which can be
     * expensive to render - are not evaluated when tracing is off.
     *
     * @param errs    the listener the traced operation is reporting through; null is recorded as
     *                such rather than hidden, because an operation with no listener at all is
     *                exactly the plumbing gap this trace exists to expose
     * @param event   short event name, e.g. {@code "build.begin"}
     * @param pool    the pool the event belongs to, or null
     * @param type    the type the event is about, or null
     * @param detail  anything else worth seeing, or null
     */
    public static void log(ErrorListener errs, String event, ConstantPool pool,
                           TypeConstant type, Object detail) {
        if (!traces(type)) {
            return;
        }

        var sb = new StringBuilder(160)
                .append(String.format("%07d", SEQ.incrementAndGet()))
                .append(" t=").append(Thread.currentThread().getName())
                .append(" p=").append(poolId(pool))
                .append(" errs=").append(listenerId(errs))
                .append(' ').append(event)
                .append(' ').append(type);
        if (detail != null) {
            sb.append(" :: ").append(detail);
        }

        if (ECHO || errs == null) {
            // -Dxvm.typeinfo.trace.echo mirrors every event to stderr. Comparing the echo against
            // what actually arrives in a caller's diagnostics is the measurement: an event that is
            // echoed but never arrives was swallowed by a silent link in the listener chain.
            // A null listener is always echoed - a build step holding no listener at all is a
            // finding, not a reason to go quiet.
            System.err.println("[TI] " + sb);
        }
        if (errs != null) {
            errs.info("TRACE-TI", sb.toString());
        }
    }

    /**
     * @return a short stable identifier for a pool - identity based, because that is precisely the
     *         question being asked (is this the SAME pool?), and it needs no state on ConstantPool
     */
    public static String poolId(ConstantPool pool) {
        return pool == null ? "-" : Integer.toHexString(System.identityHashCode(pool));
    }

    /**
     * @return a short identifier for a listener, marking a blackhole explicitly - "which listener"
     *         and "is anyone listening at all" are the two things a plumbing question turns on
     */
    public static String listenerId(ErrorListener errs) {
        return errs == null
                ? "none"
                : errs.getClass().getSimpleName() + '@'
                        + Integer.toHexString(System.identityHashCode(errs))
                        + (errs.isSilent() ? ":SILENT" : "");
    }

    /**
     * @return a short rendering of a TypeInfo's build progress, for before/after pairs
     */
    public static String progress(TypeInfo info) {
        return info == null ? "null"
                : info.isPlaceHolder() ? "Building" : String.valueOf(info.getProgress());
    }
}
