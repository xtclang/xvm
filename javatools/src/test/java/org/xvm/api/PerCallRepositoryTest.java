package org.xvm.api;


import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * One engine can compile against different libraries, chosen per call.
 *
 * <p>Which library a request compiles against is a property of the request, not of the host - the
 * shape the upstream {@code LspSupport.compile(source, input, errs)} already has. Binding it to
 * the engine forces one engine per module path, which is why the Gradle plugin keeps a
 * {@code Map<List<File>, XtcEngine>} of them.</p>
 *
 * <p>Proven by asking the SAME engine to compile the same source twice: once against a library
 * that can satisfy it, and once against one that cannot. A per-call repository is the only way the
 * second can fail while the first succeeds.</p>
 */
public class PerCallRepositoryTest {
    @Test
    public void oneEngineTwoLibraries() {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        Path root  = XdkOutputs.root();
        File lib   = root.resolve("xdk/build/install/xdk/lib").toFile();
        File tools = root.resolve("xdk/build/install/xdk/javatools").toFile();
        Path src   = root.resolve("manualTests/src/main/x/misc.x");
        assumeTrue(src.toFile().isFile(), "misc.x is required");

        try (var engine = XtcEngine.builder().modulePath(lib, tools).build()) {
            var good = engine.compile(XtcEngine.ModuleSource.of(src));
            assertTrue(good.isSuccess(), () -> "the engine's own library must work: " + good.diagnostics());

            // the turtle prototype lives under javatools, so a library without it cannot satisfy
            // any module - the same engine, a different answer, decided by the argument
            var poor = engine.compile(new DirRepository(tools, true),
                    XtcEngine.ModuleSource.of(src));
            assertFalse(poor.isSuccess(),
                    "a per-call repository that cannot satisfy the module must fail the compile");

            // and the engine is not left damaged by the failure
            var again = engine.compile(XtcEngine.ModuleSource.of(src));
            assertTrue(again.isSuccess(),
                    () -> "the engine must still compile after a failed per-call compile: "
                            + again.diagnostics());
        }
    }
}
