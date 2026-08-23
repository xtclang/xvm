package org.xvm.runtime;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Component;
import org.xvm.asm.Constants;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template._native.fs.xOSStorage;
import org.xvm.runtime.template._native.io.xTerminalConsole;
import org.xvm.runtime.template._native.reflect.xRTComponentTemplate;
import org.xvm.runtime.template._native.reflect.xRTType;
import org.xvm.runtime.template._native.reflect.xRTTypeTemplate;
import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template.numbers.xNibble;
import org.xvm.runtime.template.numbers.xUInt8;
import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xVar;
import org.xvm.runtime.template.text.xChar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Tests for the native-template owner lookup table.
 */
public class NativeTemplatesTest {
    /**
     * Template lookup must always have an owner container. Accepting null would recreate the old
     * implicit global lookup problem under a different API.
     */
    @Test
    public void rejectsNullContainer() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Container) null));
    }

    /**
     * Frame-based lookup must derive the owner from a real frame. A null frame cannot safely select
     * owner-local templates or caches.
     */
    @Test
    public void rejectsNullFrame() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((Frame) null));
    }

    /**
     * Template-based lookup must not accept a null template. The caller must supply an owner-bearing
     * object so the table cannot fall back to process-global state.
     */
    @Test
    public void rejectsNullTemplate() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get((ClassTemplate) null));
    }

    /**
     * A template without an owner is invalid for NativeTemplates lookup. This catches partial or
     * test-only template objects before they can hide owner mistakes.
     */
    @Test
    public void rejectsTemplateWithNullOwner() {
        assertThrows(NullPointerException.class, () -> NativeTemplates.get(new NullOwnerTemplate()));
    }

    /**
     * Throwable translation creates runtime handles and therefore needs an owner. The old ownerless
     * static helper shape must stay removed.
     */
    @Test
    public void throwableTranslationRequiresOwner() {
        assertThrows(NoSuchMethodException.class,
                () -> Utils.class.getMethod("translate", Throwable.class));
        assertThrows(NullPointerException.class,
                () -> Utils.translate(null, new CancellationException()));
    }

    /**
     * The table itself is owner-local. Constructing one without a container would be another
     * process-global cache in disguise.
     */
    @Test
    public void rejectsNullOwnerAtConstruction() {
        assertThrows(NullPointerException.class, () -> new NativeTemplates(null));
    }

    /**
     * A TypeHandle contains container-owned native Type object state. Caching it on TypeConstant
     * let two containers that share one pool reuse the first container's handle. The cache must live
     * on Container, where the owner dimension is explicit and publication is concurrent-map safe.
     */
    @Test
    public void typeHandlesAreCachedByContainerOwner() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> TypeConstant.class.getDeclaredField("m_handle"));

        var field = Container.class.getDeclaredField("f_mapTypeHandles");
        assertTrue(ConcurrentMap.class.isAssignableFrom(field.getType()));
    }

    /**
     * LocalClock's timer is process-wide scheduler state, not a mutable public runtime cache. It
     * must be private/final so callers cannot swap it during same-JVM runs.
     */
    @Test
    public void localClockTimerIsPrivateFinalScheduler() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xLocalClock.class.getField("TIMER"));

        assertPrivateStaticFinal(xLocalClock.class, "TIMER");
    }

    /**
     * Making the LocalClock scheduler private/final must preserve behavior. This proves scheduled
     * timer tasks still run after the field visibility cleanup.
     */
    @Test
    public void localClockSchedulerRunsTimerTasks() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        xLocalClock.scheduleTimer(new TimerTask() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 1);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /**
     * The OS storage watcher is process service state, but it must not be a public mutable holder.
     * Keeping the daemon holder private/final prevents external replacement.
     */
    @Test
    public void osStorageWatcherIsPrivateFinalHolder() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xOSStorage.class.getDeclaredField("s_daemonWatch"));

        assertPrivateStaticFinal(xOSStorage.class, "WATCH_DAEMON");
    }

    /**
     * The watch daemon must bind each event owner's pool, not cache the first caller's pool. A cached
     * pool field would route later events through stale owner state.
     */
    @Test
    public void osStorageWatcherDoesNotCacheFirstCallerPool() throws Exception {
        Class<?> clzDaemon = Class.forName(xOSStorage.class.getName() + "$WatchServiceDaemon");

        assertThrows(NoSuchFieldException.class, () -> clzDaemon.getDeclaredField("f_pool"));
        assertFalse(Arrays.stream(clzDaemon.getDeclaredFields())
                .anyMatch(field -> ConstantPool.class.isAssignableFrom(field.getType())),
                "watch daemon must bind the event owner's pool, not cache a ConstantPool field");
    }

    /**
     * Terminal console process state must be hidden behind a private/final holder. Public mutable
     * statics would make same-JVM test runs depend on external mutation order.
     */
    @Test
    public void terminalConsoleStateIsPrivateFinalHolder() throws Exception {
        assertThrows(NoSuchFieldException.class, () -> xTerminalConsole.class.getField("READER"));
        assertThrows(NoSuchFieldException.class, () -> xTerminalConsole.class.getField("TERMINAL"));

        assertPrivateStaticFinal(xTerminalConsole.class, "TERMINAL_STATE");
    }

    /**
     * The remaining small-value native caches must no longer depend on the legacy fInstance flag.
     * This verifies the explicit owner/cache replacement preserves the old cache sizes.
     */
    @Test
    public void removedFInstanceUsersHaveExplicitOwnerState() throws Exception {
        assertNoFInstanceApi(xChar.class);
        assertNoFInstanceApi(xNibble.class);
        assertNoFInstanceApi(xUInt8.class);
        assertNoFInstanceApi(xRef.class);
        assertNoFInstanceApi(xVar.class);

        assertFinalCacheArray(xChar.class, "cache", 128);
        assertFinalCacheArray(xNibble.class, "cache", 16);
        assertFinalCacheArray(xUInt8.class, "cache", 256);
    }

    /**
     * Reflective enum helpers must return initialized ObjectHandle values, not raw construction
     * structs. That was the enum singleton race behind the startup fix.
     */
    @Test
    public void reflectionEnumPublicationHelpersReturnInitializedHandles()
            throws Exception {
        assertReturnsObjectHandle(xRTType.class.getDeclaredMethod(
                "ensureAccessHandle", Frame.class, Constants.Access.class));
        assertReturnsObjectHandle(xRTType.class.getDeclaredMethod(
                "ensureFormHandle", Frame.class, TypeConstant.class));
        assertReturnsObjectHandle(xRTTypeTemplate.class.getDeclaredMethod(
                "ensureAccessHandle", Frame.class, Constants.Access.class));
        assertReturnsObjectHandle(xRTTypeTemplate.class.getDeclaredMethod(
                "ensureFormHandle", Frame.class, TypeConstant.class));
        assertReturnsObjectHandle(xRTComponentTemplate.class.getDeclaredMethod(
                "ensureFormatHandle", Frame.class, Component.Format.class));
    }

    private static void assertPrivateStaticFinal(Class<?> clz, String sField)
            throws NoSuchFieldException {
        Field field = clz.getDeclaredField(sField);
        int   mods  = field.getModifiers();

        assertTrue(Modifier.isPrivate(mods));
        assertTrue(Modifier.isStatic(mods));
        assertTrue(Modifier.isFinal(mods));
    }

    private static void assertReturnsObjectHandle(Method method) {
        assertEquals(ObjectHandle.class, method.getReturnType(),
                method + " must publish initialized or deferred enum handles, not raw EnumHandle structs");
    }

    private static void assertNoFInstanceApi(Class<?> clz) {
        assertThrows(NoSuchFieldException.class, () -> clz.getDeclaredField("f_fInstance"));
        assertFalse(Arrays.stream(clz.getDeclaredConstructors()).anyMatch(constructor -> {
            Class<?>[] params = constructor.getParameterTypes();
            return params.length == 3
                    && params[0] == Container.class
                    && params[1] == ClassStructure.class
                    && params[2] == boolean.class;
        }), clz.getName() + " must not expose the legacy fInstance constructor");
    }

    private static void assertFinalCacheArray(Class<?> clz, String sField, int cExpected)
            throws NoSuchFieldException {
        Field field = clz.getDeclaredField(sField);
        int   mods  = field.getModifiers();

        assertTrue(Modifier.isPrivate(mods));
        assertFalse(Modifier.isStatic(mods));
        assertTrue(Modifier.isFinal(mods));
        assertEquals(ObjectHandle.JavaLong[].class, field.getType());
        assertPrivateStaticFinalInt(clz, "CACHE_SIZE", cExpected);
    }

    private static void assertPrivateStaticFinalInt(Class<?> clz, String sField, int nExpected)
            throws NoSuchFieldException {
        Field field = clz.getDeclaredField(sField);
        int   mods  = field.getModifiers();

        field.setAccessible(true);
        assertTrue(Modifier.isPrivate(mods));
        assertTrue(Modifier.isStatic(mods));
        assertTrue(Modifier.isFinal(mods));
        assertEquals(int.class, field.getType());
        try {
            assertEquals(nExpected, field.getInt(null));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static class NullOwnerTemplate
            extends ClassTemplate {
        NullOwnerTemplate() {
            super(null, null);
        }
    }
}
