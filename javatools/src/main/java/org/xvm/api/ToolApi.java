package org.xvm.api;


import java.io.File;
import java.io.IOException;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Severity;


/**
 * The contract a TOOL embeds to compile and run Ecstasy in-process: an LSP server, a build daemon,
 * an IDE plugin, a test runner.
 *
 * <p>This interface exists to make that contract explicit and shared, rather than implicit in one
 * class. {@link XtcEngine} implements it; the upstream {@code ToolConnector} is the other intended
 * implementation, and the two were designed against the same requirements
 * (see {@code docs/reentrancy/toolconnector-api-proposal.md}). Naming the contract means a caller
 * can be written against {@code ToolApi} and satisfied by either.</p>
 *
 * <h2>What the contract guarantees</h2>
 * <ol>
 *   <li><b>The runtime is booted once and reused.</b> An implementation is a resident engine, not a
 *       per-invocation launcher: the whole point is to stop forking a JVM and re-bootstrapping the
 *       runtime for every compile and run.</li>
 *   <li><b>Diagnostics are never silently lost.</b> A failed compile returns a result that carries
 *       its diagnostics, AND streams them to the caller's {@link ErrorListener} as they are
 *       produced. The listener is TOTAL - pass {@link ErrorListener#BLACKHOLE} to discard - so no
 *       implementation or caller needs a null check.</li>
 *   <li><b>Runs are observable.</b> Starting a run yields a {@link RunControl}, not a bare future:
 *       a long-running host needs to ASK about a run it started (is it going, when did it start and
 *       stop, what did it return, can I stop it).</li>
 *   <li><b>Nothing handed in is aliased.</b> Inputs are immutable value records passed as varargs,
 *       never mutable collections the caller retains a live reference to.</li>
 * </ol>
 *
 * <p>Closing an implementation releases its runtime.</p>
 */
public interface ToolApi
        extends AutoCloseable {
    // ----- compile -------------------------------------------------------------------------------

    /**
     * Compile one or more in-memory module sources together, resolving cross-module references
     * among them.
     *
     * @param errs   the caller's diagnostic sink; {@link ErrorListener#BLACKHOLE} to discard
     * @param units  the module sources (name + text) as immutable value records
     *
     * @return the compile result, which always carries its diagnostics
     *
     * @throws IOException  if the compiled modules cannot be stored; the underlying
     *                      {@code ModuleRepository.storeModule} declares this, and it is propagated
     *                      rather than wrapped in an unchecked exception so a caller that can handle
     *                      a failed write is given the chance to
     */
    @NotNull CompileResult compile(@NotNull ErrorListener errs, @NotNull SourceUnit @NotNull... units)
            throws IOException;

    // ----- run -----------------------------------------------------------------------------------

    /**
     * Run a compiled module and return a handle for observing and controlling it.
     *
     * @param result       a successful {@link #compile} result containing the module
     * @param sModuleName  the module to run
     *
     * @return a control handle for the running module
     */
    @NotNull RunControl start(@NotNull CompileResult result, @NotNull String sModuleName);

    @Override
    void close();

    // ----- shared types --------------------------------------------------------------------------

    /**
     * One in-memory module source: the module's name plus its source text.
     *
     * @param moduleName  the module's name (for diagnostics/labels)
     * @param source      the module source text
     */
    record SourceUnit(@NotNull String moduleName, @NotNull String source) {
    }

    /**
     * A structured compile/run diagnostic. {@code code} is the stable identifier a tool should
     * switch on, rather than parsing {@code message} - the same reasoning behind the upstream
     * {@code ToolConnector}'s {@code TC-xx} vocabulary.
     *
     * @param severity  the severity
     * @param code      the message code
     * @param message   the rendered message text
     * @param source    the source name/uri, or null
     * @param line      the 1-based line, or 0 if unknown
     */
    record Diagnostic(@NotNull Severity severity, @NotNull String code, @NotNull String message,
                      @Nullable String source, int line) {
    }

    /**
     * The outcome of a compile: the compiled module ids (empty on failure), every diagnostic
     * produced, and the in-memory repository holding the compiled modules.
     */
    interface CompileResult {
        /** @return the compiled module ids; empty on failure */
        @NotNull List<ModuleConstant> modules();

        /** @return every diagnostic produced, whether or not the compile succeeded */
        @NotNull List<Diagnostic> diagnostics();

        /** @return the in-memory repository holding the compiled modules */
        @NotNull BuildRepository buildRepository();

        /** @return true iff modules were produced and nothing at ERROR or above was reported */
        boolean isSuccess();

        /**
         * Persist the compiled modules to disk as {@code .xtc} binaries - the "sync to disk" half of
         * the in-memory-first model.
         *
         * @param dir  the output directory (created if absent)
         *
         * @return the files written, one per module
         */
        @NotNull List<File> writeTo(@NotNull File dir) throws IOException;
    }

    /**
     * Management and monitoring for a running module - the same role as the upstream
     * {@code ToolConnector.Control}, with absence expressed as {@link Optional} rather than null so
     * "still running", "failed", and "returned nothing" cannot be confused.
     */
    interface RunControl {
        /** @return true iff the module is still running */
        boolean running();

        /** @return when the run started */
        @NotNull Instant whenStarted();

        /** @return when the run stopped, or empty while it is still running */
        @NotNull Optional<Instant> whenStopped();

        /** @return the module's {@code run()} result once it completed normally, else empty */
        @NotNull Optional<Long> result();

        /** @return the failure if the run completed exceptionally, else empty */
        @NotNull Optional<Throwable> error();

        /** Stop the run as promptly as the implementation allows. */
        void kill();

        /** @return the event-driven completion future, for callers that prefer to await it */
        @NotNull CompletableFuture<ObjectHandle> completion();
    }
}
