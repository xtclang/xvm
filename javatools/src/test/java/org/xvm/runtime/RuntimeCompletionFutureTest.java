package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.tool.Compiler;
import org.xvm.tool.Console;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Foundation for the first-class Java embedding API (issue 543 items 1a/1b): the run entry point
 * must give the caller an EVENT-DRIVEN completion future that carries a real failure - not a bare
 * int reported after an idle poll. The runtime already produces exactly this future at the
 * service-context boundary ({@code ServiceContext.callLater} returns a
 * {@code CompletableFuture<ObjectHandle>} that completes exceptionally with the XTC
 * {@code ExceptionHandle}); {@code MainContainer.invoke0} used to discard it. This test pins that
 * {@code MainContainer.futureResult()} now exposes it: a clean run completes the future normally,
 * and a throwing run completes it EXCEPTIONALLY with the thrown exception's text - awaitable with
 * a deadline, no busy-wait.
 */
public class RuntimeCompletionFutureTest {
    @Test
    public void cleanRunCompletesTheFutureNormally() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        withRun("CompletionOk", """
                module CompletionOk {
                    void run() {
                        @Inject Console console;
                        console.print("ran cleanly");
                    }
                }
                """, (container) -> {
            var future = container.futureResult();
            assertNotNull(future, "invoke0 must publish a completion future");
            future.get(30, TimeUnit.SECONDS); // completes normally, no exception
            assertTrue(future.isDone() && !future.isCompletedExceptionally());
        });
    }

    @Test
    public void throwingRunCompletesTheFutureExceptionallyWithTheCause() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        withRun("CompletionThrow", """
                module CompletionThrow {
                    void run() {
                        throw new IllegalState("boom-from-xtc");
                    }
                }
                """, (container) -> {
            var future = container.futureResult();
            assertNotNull(future);
            var thrown = assertThrows(ExecutionException.class,
                    () -> future.get(30, TimeUnit.SECONDS),
                    "a run that threw must complete the future EXCEPTIONALLY, not silently");
            assertTrue(String.valueOf(thrown.getCause()).contains("boom-from-xtc")
                            || String.valueOf(thrown).contains("boom-from-xtc"),
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
        compile(dirWork, dirOut, sModule, sSource);

        ModuleRepository repository = new LinkedRepository(
                new DirRepository(dirOut.toFile(), true), systemRepository());

        var runtime = new Runtime();
        try {
            runtime.start();
            var containerNative = NativeContainer.create(runtime, repository);

            ModuleStructure moduleApp = repository.loadModule(sModule);
            FileStructure   structApp = containerNative.createFileStructure(moduleApp);
            ModuleConstant  idMissing = structApp.linkModules(repository, true);
            assertNull(idMissing, "app module must link");

            var containerMain = MainContainer.create(runtime, containerNative, structApp.getModuleId());
            containerMain.start(java.util.Map.of());
            containerMain.invoke0("run");

            assertion.check(containerMain);
        } finally {
            runtime.shutdownXVM();
        }
    }

    private static void assertNull(Object o, String msg) {
        if (o != null) {
            throw new AssertionError(msg + " (was " + o + ")");
        }
    }

    private static void compile(Path dirWork, Path dirOut, String sModule, String sSource)
            throws Exception {
        Path source = dirWork.resolve(sModule + ".x");
        Files.writeString(source, sSource);
        var builder = CompilerOptions.builder()
                .addInputFile(source.toString())
                .setOutputLocation(dirOut.toString());
        for (String path : new String[] {"xdk/build/install/xdk/lib",
                "xdk/build/install/xdk/javatools", "lib_ecstasy/build/xtc/main/lib"}) {
            File dir = checkoutFile(path);
            if (dir.isDirectory()) {
                builder.addModulePath(dir.getAbsolutePath());
            }
        }
        var errs   = new ErrorList(25);
        int result = new Compiler(builder.build(), silentConsole(), errs).run();
        if (result != 0 || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of " + sModule + " failed: " + errs.getErrors());
        }
    }

    private static Console silentConsole() {
        return new Console() {
            @Override
            public String log(Severity sev, String template, Object... params) {
                return Console.formatTemplate(template, params);
            }
        };
    }

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static ModuleRepository systemRepository() {
        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib",
                "xdk/build/install/xdk/javatools")
                .map(RuntimeCompletionFutureTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory() ? new DirRepository(directory, true) : null;
    }

    private static File checkoutFile(String path) {
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }
}
