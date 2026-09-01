package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;

import org.xvm.asm.op.Return_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Behavioral tests for the native alarm callback registry. These boot a real {@link
 * NativeContainer} over the compiled system modules and drive the real runtime classes.
 */
public class NativeCallbackRegistrationTest {
    /**
     * The registry is shared between the service thread that registers callbacks and the
     * process-wide Java timer thread that extracts them when an alarm matures, so it must be a live
     * concurrent map from the moment the service exists. It used to be a plain HashMap published
     * lazily through a non-final field, which meant it was null until someone happened to register
     * the first callback, and unsynchronized forever after.
     */
    @Test
    public void callbackRegistryIsLiveBeforeAnyCallbackIsRegistered() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = newRuntime().createServiceContext("registry");

        assertNotNull(context.getCallbackMap(),
                "the callback registry must exist before the first callback is registered");
        assertTrue(context.getCallbackMap().isEmpty(),
                "a fresh service must start with an empty callback registry");
    }

    /**
     * Extraction runs on the shared Java timer thread. A callback that is already gone - because
     * the service was collected, or because a racing cancel discarded it - used to raise
     * IllegalStateException there. An exception escaping a TimerTask kills the shared static Timer,
     * which silently disables every alarm in every container in the process, so a missing callback
     * has to be an ordinary null result instead.
     */
    @Test
    public void extractingAMissingCallbackReturnsNullInsteadOfThrowing() {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = newRuntime().createServiceContext("extract");
        WeakCallback   ref     = new WeakCallback(entryFrame(context), null);

        assertNotNull(ref.extractCallback(), "the first extraction must hand back the callback");
        assertNull(ref.extractCallback(),
                "extracting an already-extracted callback must not throw on the timer thread");
        assertTrue(context.getCallbackMap().isEmpty(),
                "extraction must remove the entry from the registry");
    }

    /**
     * The real cross-thread access pattern: the owning service registers callbacks on its own
     * thread while previously scheduled alarms mature and extract them on the Java timer thread,
     * with no monitor in common. Against the old plain HashMap a put that resizes the table racing
     * a remove could lose an entry or corrupt the map outright.
     */
    @Test
    public void registryToleratesConcurrentServiceAndTimerThreadAccess() throws InterruptedException {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = newRuntime().createServiceContext("race");
        Frame          frame   = entryFrame(context);

        var pending   = new ArrayBlockingQueue<WeakCallback>(QUEUE_DEPTH);
        var failures  = new CopyOnWriteArrayList<Throwable>();
        var extracted = new AtomicInteger();

        // stands in for the shared Java timer thread draining matured alarms
        Thread timer = new Thread(() -> {
            for (int i = 0; i < CALLBACK_COUNT; i++) {
                try {
                    if (pending.take().extractCallback() != null) {
                        extracted.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failures.add(e);
                    return;
                }
            }
        }, "test-timer-thread");
        timer.setDaemon(true);
        timer.start();

        // this thread stands in for the owning service registering new alarms
        for (int i = 0; i < CALLBACK_COUNT; i++) {
            pending.put(new WeakCallback(frame, null));
        }
        timer.join(JOIN_TIMEOUT_MILLIS);

        assertTrue(failures.isEmpty(),
                () -> "the timer thread must never see a corrupt or lost registry entry, but got: "
                        + failures.getFirst());
        assertFalse(timer.isAlive(),
                "the timer thread must not be stuck inside the callback registry");
        assertEquals(CALLBACK_COUNT, extracted.get(),
                "every registered callback must be extractable exactly once");
        assertTrue(context.getCallbackMap().isEmpty(),
                "a fully drained registry must be empty");
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a fresh primordial container over the compiled system modules
     */
    private static NativeContainer newRuntime() {
        return new NativeContainer(new Runtime(), systemRepository());
    }

    /**
     * Create a native entry frame for the specified service, of the shape a native method receives.
     */
    private static Frame entryFrame(ServiceContext context) {
        var message = new ServiceContext.Message(null) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public int getCallDepth() {
                return 0;
            }

            @Override
            public ObjectHandle getTimeoutHandle() {
                return null;
            }

            @Override
            public long getTimeoutStamp() {
                return 0L;
            }

            @Override
            Frame createFrame(ServiceContext ctx) {
                return ctx.createServiceEntryFrame(this, 0, NATIVE_OPS);
            }
        };
        return context.createServiceEntryFrame(message, 0, NATIVE_OPS);
    }

    private static boolean systemModulesAvailable() {
        ModuleRepository repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    /**
     * Test-only locator for the gradle build outputs that hold the compiled system modules.
     */
    private static ModuleRepository systemRepository() {
        var repositories = SYSTEM_MODULE_PATHS.stream()
                .map(NativeCallbackRegistrationTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();
        return repositories.isEmpty()
                ? null
                : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
    }

    private static ModuleRepository repositoryFor(String path) {
        File directory = checkoutFile(path);
        return directory.isDirectory() ? new DirRepository(directory, true) : null;
    }

    private static File checkoutFile(String path) {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final List<String> SYSTEM_MODULE_PATHS = List.of(
            "lib_ecstasy/build/xtc/main/lib",
            "javatools_bridge/build/xtc/main/lib",
            "xdk/build/install/xdk/lib",
            "xdk/build/install/xdk/javatools");

    private static final Op[] NATIVE_OPS = new Op[]{Return_0.INSTANCE};

    private static final int CALLBACK_COUNT = 50_000;

    private static final int QUEUE_DEPTH = 256;

    private static final long JOIN_TIMEOUT_MILLIS = 60_000L;
}
