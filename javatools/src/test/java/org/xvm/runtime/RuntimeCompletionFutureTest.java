package org.xvm.runtime;


import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.DirRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Foundation for the first-class Java embedding API (issue 543 items 1a/1b): the run entry point
 * must give the caller an EVENT-DRIVEN completion future that carries a real failure - not a bare
 * int reported after an idle poll. The runtime already produces exactly this future at the
 * service-context boundary ({@code ServiceContext.callLater} returns a
 * {@code CompletableFuture<ObjectHandle>} that completes exceptionally with the XTC
 * {@code ExceptionHandle}); {@code MainContainer.invoke0} discarded it. This test pins that
 * {@code MainContainer.futureResult()} now exposes it: a clean run completes the future normally,
 * and a throwing run completes it EXCEPTIONALLY with the thrown exception's text - awaitable with
 * a deadline, no busy-wait.
 */
public class RuntimeCompletionFutureTest {
    @Test
    public void cleanRunCompletesTheFutureNormally() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        withRun("CompletionOk", """
                module CompletionOk {
                    void run() {
                        @Inject Console console;
                        console.print("ran cleanly");
                    }
                }
                """, container -> {
            var future = container.futureResult();
            assertNotNull(future, "invoke0 must publish a completion future");
            future.get(30, TimeUnit.SECONDS); // completes normally, no exception
            assertTrue(future.isDone() && !future.isCompletedExceptionally());
        });
    }

    @Test
    public void throwingRunCompletesTheFutureExceptionallyWithTheCause() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        withRun("CompletionThrow", """
                module CompletionThrow {
                    void run() {
                        throw new IllegalState("boom-from-xtc");
                    }
                }
                """, container -> {
            var future = container.futureResult();
            assertNotNull(future);
            var thrown = assertThrows(ExecutionException.class,
                    () -> future.get(30, TimeUnit.SECONDS),
                    "a run that threw must complete the future EXCEPTIONALLY, not silently");
            assertTrue(String.valueOf(thrown).contains("boom-from-xtc"),
                    "the completion future must carry the thrown cause, got: " + thrown);
        });
    }

    // ----- fixture ------------------------------------------------------------------------------

    @FunctionalInterface
    private interface RunAssertion {
        void check(MainContainer container) throws Exception;
    }

    private void withRun(String sModule, String sSource, RunAssertion assertion) throws Exception {
        Path dirWork = Files.createTempDirectory("completion-future");
        Path dirOut  = Files.createDirectory(dirWork.resolve("lib"));
        EmbeddingTestSupport.compile(dirWork, dirOut, sModule, sSource);

        ModuleRepository repository = new LinkedRepository(
                new DirRepository(dirOut.toFile(), true), EmbeddingTestSupport.systemRepository());

        var runtime = new Runtime();
        try {
            runtime.start();
            var containerNative = NativeContainer.create(runtime, repository, ErrorListener.RUNTIME);

            ModuleStructure moduleApp = repository.loadModule(sModule);
            FileStructure   structApp = containerNative.createFileStructure(moduleApp);
            ModuleConstant  idMissing = structApp.linkModules(repository, true);
            assertNull(idMissing, "app module must link");

            var containerMain = MainContainer.create(runtime, containerNative, structApp.getModuleId());
            containerMain.start(Map.of());
            containerMain.invoke0("run");

            assertion.check(containerMain);
        } finally {
            runtime.shutdownXVM();
        }
    }
}
