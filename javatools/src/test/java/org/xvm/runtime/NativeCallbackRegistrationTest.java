package org.xvm.runtime;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards native callback keep-alive registration against exception-path leaks.
 */
public class NativeCallbackRegistrationTest {
    /**
     * LocalClock used to register a keep-alive callback from the Alarm constructor. If scheduling
     * failed, TimerTask.cancel() could report false for the unscheduled trigger and leave the
     * container callback count permanently elevated.
     */
    @Test
    public void localClockKeepAliveIsNotConstructorWork() throws IOException {
        var source      = readString("org/xvm/runtime/template/_native/temporal/xLocalClock.java");
        var constructor = sourceBetween(source,
                "protected Alarm(WeakCallback refCallback", "public Trigger getTrigger()");

        assertFalse(constructor.contains("registerNativeCallback"),
                "Alarm construction must not claim container keep-alive ownership");
        assertTrue(source.contains("public void registerKeepAlive()"),
                "LocalClock must register keep-alive as part of the schedule attempt");
        assertTrue(source.contains("cancelAfterScheduleFailure()"),
                "LocalClock must roll back registration independently of TimerTask.cancel()");
    }

    /**
     * NanoTimer used to catch Throwable after registering the native callback, cancel the trigger,
     * and return success. That swallowed scheduler failure and could strand keep-alive ownership if
     * the weak callback had already disappeared or TimerTask.cancel() could not unwind the
     * registration.
     */
    @Test
    public void nanosTimerPropagatesAndRollsBackScheduleFailure() throws IOException {
        var source = readString("org/xvm/runtime/template/_native/temporal/xNanosTimer.java");

        assertFalse(source.contains("catch (Throwable e) {\n                    cancelTrigger();"),
                "NanoTimer must not swallow scheduler failure after registering keep-alive");
        assertTrue(source.contains("catch (RuntimeException | Error e)"),
                "NanoTimer must roll back the schedule attempt "
                        + "and propagate JVM scheduler failure");
        assertTrue(source.contains("m_containerRegistered"),
                "NanoTimer must remember the exact registered owner for later cleanup");
    }

    /**
     * Server bind used to register the container callback before the final context setup. If a
     * later startup step failed, the service context was terminated but the callback count and
     * partial Java server resources remained owned by the container.
     */
    @Test
    public void serverBindFailureRollsBackCallbackAndResources() throws IOException {
        var source   = readString("org/xvm/runtime/template/_native/web/xRTServer.java");
        var rollback = sourceBetween(source,
                "private static void rollbackBind(", "private static void closeServerQuietly");

        assertTrue(source.contains("boolean         fCallbackRegistered = false;"),
                "server bind must track whether keep-alive registration happened");
        assertTrue(source.contains("rollbackBind(hServer, executor, fCallbackRegistered);"),
                "server bind failure must run rollback before raising the XTC exception");
        assertTrue(rollback.contains("unregisterNativeCallback"),
                "server bind rollback must release the container callback count");
        assertTrue(rollback.contains("closeServerQuietly"),
                "server bind rollback must close partially configured Java servers");
        assertTrue(rollback.contains("executor.shutdown()"),
                "server bind rollback must shut down the partially created executor");
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
}
