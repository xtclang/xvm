package org.xvm.runtime;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;

import org.xvm.runtime.template._native.fs.xOSStorage;
import org.xvm.runtime.template._native.io.xTerminalConsole;
import org.xvm.runtime.template._native.temporal.xLocalClock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Tests for the native-template owner lookup table.
 */
public class NativeTemplatesTest {
    @Test
    public void rejectsNullContainer() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Container) null));
    }

    @Test
    public void rejectsNullFrame() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Frame) null));
    }

    @Test
    public void rejectsNullTemplate() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((ClassTemplate) null));
    }

    @Test
    public void rejectsTemplateWithNullOwner() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get(new NullOwnerTemplate()));
    }

    @Test
    public void throwableTranslationRequiresOwner() {
        assertThrows(NoSuchMethodException.class,
                () -> Utils.class.getMethod("translate", Throwable.class));
        assertThrows(NullPointerException.class,
                () -> Utils.translate(null, new CancellationException()));
    }

    @Test
    public void rejectsNullOwnerAtConstruction() {
        assertThrows(NullPointerException.class, () -> new NativeTemplates(null));
    }

    @Test
    public void localClockTimerIsPrivateFinalScheduler()
            throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xLocalClock.class.getField("TIMER"));

        assertPrivateStaticFinal(xLocalClock.class, "TIMER");
    }

    @Test
    public void localClockSchedulerRunsTimerTasks()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        xLocalClock.scheduleTimer(new TimerTask() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 1);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void osStorageWatcherIsPrivateFinalHolder()
            throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xOSStorage.class.getDeclaredField("s_daemonWatch"));

        assertPrivateStaticFinal(xOSStorage.class, "WATCH_DAEMON");
    }

    @Test
    public void osStorageWatcherDoesNotCacheFirstCallerPool()
            throws Exception {
        Class<?> clzDaemon = Class.forName(xOSStorage.class.getName() + "$WatchServiceDaemon");

        assertThrows(NoSuchFieldException.class, () -> clzDaemon.getDeclaredField("f_pool"));
        assertFalse(Arrays.stream(clzDaemon.getDeclaredFields())
                .anyMatch(field -> ConstantPool.class.isAssignableFrom(field.getType())),
                "watch daemon must bind the event owner's pool, not cache a ConstantPool field");
    }

    @Test
    public void terminalConsoleStateIsPrivateFinalHolder()
            throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xTerminalConsole.class.getField("READER"));
        assertThrows(NoSuchFieldException.class, () -> xTerminalConsole.class.getField("TERMINAL"));

        assertPrivateStaticFinal(xTerminalConsole.class, "TERMINAL_STATE");
    }

    private static void assertPrivateStaticFinal(Class<?> clz, String sField)
            throws NoSuchFieldException {
        Field field = clz.getDeclaredField(sField);
        int   mods  = field.getModifiers();

        assertTrue(Modifier.isPrivate(mods));
        assertTrue(Modifier.isStatic(mods));
        assertTrue(Modifier.isFinal(mods));
    }

    private static class NullOwnerTemplate
            extends ClassTemplate {
        NullOwnerTemplate() {
            super(null, null);
        }
    }
}
