package org.xvm.runtime;


import java.io.File;

import java.nio.file.Path;

import java.time.Duration;

import java.util.List;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.xvm.api.XtcEngine;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * A run started through {@link XtcEngine} must not leave its file-system root behind.
 *
 * <p>The engine uses {@code registerTransientTask}, so the runner deletes the task's root as soon as
 * the run completes. Upstream's {@code registerTask} keeps it, because a {@code Control} is handed to
 * a caller who may want to inspect what the run produced - that is the right default there and the
 * wrong one here, since this engine hands out no {@code Control} and exposes no task directory.
 *
 * <p>The root is {@code curDir.dirFor("{moduleName}_{id}")} (see {@code runner.x}
 * {@code taskDirectoryName}), which is what makes the property observable at all: the engine gives
 * the caller no way to name it, but the process working directory does.
 *
 * <p><b>The module touches its file system on purpose.</b> The store is {@code @Lazy}, so a run that
 * never uses it would leave no directory whether or not deletion works, and the test would pass
 * without testing anything. Creating a directory under {@code tmpDir} forces the root into existence
 * first.
 *
 * <p><b>And the wait is not a workaround.</b> The runner completes the caller's future BEFORE it asks
 * for the deletion, and asks with {@code ^}:
 *
 * <pre>{@code
 * completion = (result, failure);                          // the caller is told "done" here
 * TaskRegistry.unregisterTask^(id);
 * if (!retainStore) {
 *     TaskRegistry.deleteTaskDirectory^(id, template.name); // async, and after the above
 * }
 * }</pre>
 *
 * So deletion is prompt and guaranteed - a scheduled service call, not a GC-triggered Cleaner - but
 * it is NOT ordered with respect to the run's future. A caller cannot assert the root is gone the
 * instant {@code run()} resolves, and this test asserting that is how the asymmetry was found.
 */
public class TransientTaskDirectoryTest {
    private static final String MODULE = "TransientProbe";

    @Test
    public void aCompletedRunLeavesNoTaskDirectory() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        File cwd = Path.of("").toAbsolutePath().toFile();
        assertTrue(leftovers(cwd).isEmpty(),
                () -> "a previous run leaked a task directory: " + leftovers(cwd));

        var root = XdkOutputs.root();
        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {

            var compiled = engine.compile(MODULE, """
                    module %s {
                        void run() {
                            // force the lazy FileStore into existence, so the root really is created
                            @Inject Directory tmpDir;
                            tmpDir.dirFor("marker").ensure();
                        }
                    }
                    """.formatted(MODULE));
            assertTrue(compiled.isSuccess(), () -> "compile failed: " + compiled.diagnostics());

            assertNotNull(engine.run(compiled, MODULE).get());
        }

        assertTrue(deletedWithin(cwd, Duration.ofSeconds(30)),
                () -> "the run's file-system root was never deleted: " + leftovers(cwd));
    }

    /**
     * @return true once no task directory remains, waiting up to the given bound
     */
    private static boolean deletedWithin(File dir, Duration bound) throws InterruptedException {
        long deadline = System.nanoTime() + bound.toNanos();
        while (System.nanoTime() < deadline) {
            if (leftovers(dir).isEmpty()) {
                return true;
            }
            Thread.sleep(50);
        }
        return leftovers(dir).isEmpty();
    }

    /**
     * @return any {@code TransientProbe_*} directory left in the working directory
     */
    private static List<String> leftovers(File dir) {
        String[] names = dir.list((d, name) -> name.startsWith(MODULE + "_"));
        return names == null ? List.of() : Arrays.asList(names);
    }
}
