package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.URISyntaxException;
import java.net.URL;

import java.time.Duration;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.xvm.api.EmbeddingSupport.Control;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.ServiceContext;

import org.xvm.runtime.template._native.reflect.xRTFunction.FullyBoundHandle;
import org.xvm.runtime.template._native.reflect.xRTSignature;

import org.xvm.runtime.template._native.temporal.xNanosTimer.TimerHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What keeps a runtime once its host is done with it, which a JVM that makes another runtime would
 * otherwise carry: its timers' pending alarms and its worker threads, which shutting its
 * interpreter down ends, and the values its templates cache, which each runtime makes for itself.
 */
public class RuntimeRetentionTest {
    private static final Duration RUN_LIMIT = Duration.ofSeconds(60);

    @Test
    public void shuttingDownAnInterpreterCancelsItsTimersAlarmsAndEndsItsThreads() throws Exception {
        EmbeddingSupport support   = run("Alarms");
        Runtime          runtime   = field(support.ensureConnector(), "f_runtime");
        NativeContainer  container = field(support.ensureConnector(), "f_containerNative");
        assertEquals(1, pendingAlarms(container), "the program's hour-long alarm is pending");

        support.shutdownInterpreter();
        assertEquals(0, pendingAlarms(container));
        assertTrue(runtime.f_executorXVM.isShutdown());
        assertTrue(runtime.f_executorIO.isShutdown());
    }

    @Test
    public void aRuntimeCachesItsOwnValuesInsteadOfKeepingThoseOfTheRuntimeBeforeIt() throws Exception {
        // the first runtime fills the caches while it is the one that runs
        EmbeddingSupport first          = run("First");
        NativeContainer  firstContainer = field(first.ensureConnector(), "f_containerNative");
        assertSame(firstContainer, ((ClassComposition) xRTSignature.ensureParamArray()).getContainer());
        assertSame(firstContainer, FullyBoundHandle.NO_OP.getComposition().getContainer());
        first.shutdownInterpreter();

        EmbeddingSupport next = run("Next");
        try {
            NativeContainer nextContainer = field(next.ensureConnector(), "f_containerNative");
            assertNotSame(firstContainer, nextContainer);
            assertSame(nextContainer, ((ClassComposition) xRTSignature.ensureParamArray()).getContainer());
            assertSame(nextContainer, FullyBoundHandle.NO_OP.getComposition().getContainer());
        } finally {
            next.shutdownInterpreter();
        }
    }

    /**
     * Run a module that leaves a timer's alarm pending, in a new EmbeddingSupport, to its end.
     */
    private static EmbeddingSupport run(String name) throws URISyntaxException {
        URL modules = RuntimeRetentionTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        File xdk = new File(modules.toURI());

        EmbeddingSupport support = new EmbeddingSupport().configure(new DirRepository(xdk, true), null);
        ErrorList        errs    = new ErrorList(25);
        ModuleStructure  module  = support.compile("""
                module %s {
                    void run() {
                        @Inject Console console;
                        @Inject Timer timer;
                        timer.start();
                        timer.schedule(Duration.ofMinutes(60), () -> console.print("dropped"));
                        console.print("returned");
                    }
                }
                """.formatted(name), new DirRepository(xdk, true), errs);
        assertNotNull(module, () -> "compile failed: " + errs.getErrors());

        Control control = support.run(module, new PrintWriter(new StringWriter(), true), null, null, errs);
        assertNotNull(control, () -> name + " did not start: " + errs.getErrors());
        try (control) {
            assertTimeoutPreemptively(RUN_LIMIT, control::join);
        }
        return support;
    }

    /**
     * @return the number of pending alarms of the container's timers
     */
    private static int pendingAlarms(NativeContainer container) throws ReflectiveOperationException {
        int count = 0;
        for (ServiceContext service : container.getServices()) {
            if (service.getService() instanceof TimerHandle timer) {
                Set<?> alarms = field(timer, "f_setAlarms");
                count += alarms.size();
            }
        }
        return count;
    }

    /**
     * @return the value of a private field of the object, or of one of its superclasses
     */
    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> clz = object.getClass(); clz != null; clz = clz.getSuperclass()) {
            try {
                var field = clz.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(object);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }
}
