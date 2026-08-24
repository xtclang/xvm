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

    /**
     * The service alarm-callback registry was a lazily created plain HashMap: the owning service
     * put entries on its own thread, but alarm maturation removed them on the process-wide static
     * Timer thread with no common monitor, so a put resize racing a timer-thread remove could
     * corrupt the map. A lost entry then made WeakCallback.extractCallback() throw
     * IllegalStateException on the Timer thread, and an exception escaping a TimerTask kills the
     * shared static Timer, silently disabling every alarm in every container. Alarm cancellation
     * also never removed its entry, leaking the captured Frame and FunctionHandle for the life of
     * the service. Found by the weak/identity registry audit (state-inventory.md, row 160).
     */
    @Test
    public void callbackRegistryIsConcurrentTimerSafeAndLeakFree() throws IOException {
        var svc = readString("org/xvm/runtime/ServiceContext.java");
        assertTrue(svc.contains(
                "private final Map<Long, WeakCallback.Callback> f_mapCallbacks = new ConcurrentHashMap<>();"),
                "the callback registry must be an eager, final, concurrent map");
        assertFalse(svc.contains("m_mapCallbacks"),
                "the registry must not be lazily published through a plain field");

        var callback = readString("org/xvm/runtime/WeakCallback.java");
        assertFalse(callback.contains("IllegalStateException"),
                "a missing callback must be a normal null result, never a timer-thread throw");
        assertTrue(callback.contains("public void discard()"),
                "cancellation must be able to remove the registry entry");

        var clock = readString("org/xvm/runtime/template/_native/temporal/xLocalClock.java");
        assertTrue(clock.contains("f_refCallback.discard();"),
                "LocalClock alarm cancellation must discard the callback entry");
        assertTrue(clock.contains("if (callback != null) {"),
                "LocalClock alarm firing must tolerate a discarded callback");

        var timer = readString("org/xvm/runtime/template/_native/temporal/xNanosTimer.java");
        assertTrue(timer.contains("f_refCallback.discard();"),
                "NanosTimer alarm cancellation must discard the callback entry");
        assertTrue(timer.contains("if (callback != null) {"),
                "NanosTimer alarm firing must tolerate a discarded callback");
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
