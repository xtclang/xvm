package org.xvm.runtime;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards runtime entry failure paths that must preserve their Java cause.
 */
public class RuntimeFailurePropagationTest {
    /**
     * MainContainer startup and invocation failures carry module, owner, and stack context in the
     * original exception. Master flattened that to a message string, making same-JVM startup and
     * ownership failures much harder to diagnose.
     */
    @Test
    public void mainContainerInvokePreservesFailureCause() throws IOException {
        var source       = readString("org/xvm/runtime/MainContainer.java");
        var fixedWrapper = "new RuntimeException(\"failed to run: \" + f_idModule, e)";

        assertFalse(source.contains("\". Cause: \" + e.getMessage()"),
                "MainContainer.invoke0 must not flatten the original cause to a message");
        assertTrue(source.contains(fixedWrapper),
                "MainContainer.invoke0 must preserve the original startup/invocation cause");
    }

    /**
     * Worker failures in service scheduling and service draining used to be printed and forgotten.
     * That let the connector eventually report normal idle completion after a Java runtime defect.
     */
    @Test
    public void workerFailuresAreRecordedForJoinBoundary() throws IOException {
        var container = readString("org/xvm/runtime/Container.java");
        var service   = readString("org/xvm/runtime/ServiceContext.java");
        var schedule  = sourceBetween(container,
                "public void schedule(ServiceContext service)", "public <R> CompletableFuture");
        var drain     = sourceBetween(service,
                "protected boolean drainWork()", "protected void ensureScheduled");

        assertTrue(schedule.contains("recordRuntimeFailure("),
                "scheduler failures must be published to the container failure slot");
        assertFalse(schedule.contains("printStackTrace(System.err)"),
                "scheduler failures must not be print-only");
        assertTrue(drain.contains("recordRuntimeFailure("),
                "service drain failures must be published to the container failure slot");
        assertFalse(drain.contains("printStackTrace(System.err)"),
                "service drain failures must not be print-only");
    }

    /**
     * Recording a terminal runtime failure is not enough unless the host boundary observes it before
     * returning a successful result. The connector must check before waiting and before returning.
     */
    @Test
    public void interpreterJoinObservesRecordedRuntimeFailure() throws IOException {
        var source = readString("org/xvm/api/InterpreterConnector.java");

        assertTrue(source.contains("m_containerMain.throwIfRuntimeFailed();"),
                "join must check container runtime failures");
        assertTrue(countOccurrences(source, "m_containerMain.throwIfRuntimeFailed();") >= 2,
                "join must check before waiting and before returning the result");
    }

    /**
     * Op implementations return natural XTC exceptions as R_EXCEPTION; unchecked Java defects
     * thrown through the central loop are runtime failures. Master made every Throwable a
     * user-catchable XTC "Run-time error".
     */
    @Test
    public void opLoopDoesNotTranslateVmDefectsToXtcRunTimeError() throws IOException {
        var service = readString("org/xvm/runtime/ServiceContext.java");
        var opLoop  = sourceBetween(service, "while (true) { // main loop", "case Op.R_RETURN_CALL");

        assertTrue(opLoop.contains("catch (RuntimeException | Error e)"),
                "unchecked Java defects must be caught separately from natural XTC results");
        assertTrue(opLoop.contains("throw unexpectedOpFailure("),
                "Java runtime defects must be sent to the host failure boundary");
        assertFalse(opLoop.contains("catch (Throwable"),
                "op execution must not catch every Throwable");
        assertFalse(opLoop.contains("frame.raiseException(\"Run-time error: \""),
                "VM defects must not become generic user-catchable XTC exceptions");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }

    private static String sourceBetween(String source, String start, String end) {
        var ofStart = source.indexOf(start);
        var ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, "missing source start marker: " + start);
        assertTrue(ofEnd > ofStart, "missing source end marker: " + end);
        return source.substring(ofStart, ofEnd);
    }

    private static int countOccurrences(String source, String pattern) {
        return (int) Pattern.compile(Pattern.quote(pattern)).matcher(source).results().count();
    }
}
