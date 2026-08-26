package org.xvm.runtime;


import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.api.EmbeddingTestSupport;

import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The linchpin of the first-class Java embedding API: a TRUSTED JAVA HOST can create a NESTED
 * container under the shared native plane and run a module in it - without any guest host module -
 * and reuse that one warm plane across runs. This is the sanctioned deployment model (one root,
 * nested children, the shape Runner.x and the platform use) finally reachable from Java, so a tool
 * (an LSP, a test runner) can deploy the correct way instead of the sibling-main / fresh-bootstrap
 * shapes.
 *
 * The test creates two nested containers under ONE NativeContainer, each running a module that
 * injects Console, and asserts each run's {@link Container#runModule} completion future completes
 * NORMALLY. A normal completion proves both that the module executed and that Console injection
 * resolved through the host container's native-plane fallback - had injection failed, the run
 * would raise "Invalid resource" and the future would complete exceptionally.
 */
public class HostNestedContainerTest {
    @Test
    public void hostRunsNestedModulesOverOneWarmNativePlane() throws Exception {
        assumeTrue(EmbeddingTestSupport.systemModulesAvailable(),
                "compiled XDK system modules are required");

        Path dirWork = Files.createTempDirectory("host-nested");
        Path dirOut  = Files.createDirectory(dirWork.resolve("lib"));
        EmbeddingTestSupport.compile(dirWork, dirOut, "NestOne", helloModule("NestOne", "first"));
        EmbeddingTestSupport.compile(dirWork, dirOut, "NestTwo", helloModule("NestTwo", "second"));

        var repository = new org.xvm.asm.LinkedRepository(
                new org.xvm.asm.DirRepository(dirOut.toFile(), true),
                EmbeddingTestSupport.systemRepository());

        var runtime = new Runtime();
        try {
            runtime.start();
            // ONE native plane, reused by both nested runs (the warm engine)
            var containerNative = NativeContainer.create(runtime, repository);

            runNestedModule(containerNative, repository, "NestOne");
            runNestedModule(containerNative, repository, "NestTwo");
        } finally {
            runtime.shutdownXVM();
        }
    }

    private void runNestedModule(NativeContainer containerNative, ModuleRepository repository,
                                 String sModule) throws Exception {
        ModuleStructure moduleApp = repository.loadModule(sModule);
        FileStructure   structApp = containerNative.createFileStructure(moduleApp);
        ModuleConstant  idMissing = structApp.linkModules(repository, true);
        assertNull(idMissing, "app module must link");

        // a nested child under the shared native plane, owned by this Java host: native-plane
        // injection fallback gives it Console/etc. with no guest provider
        var containerNested = NestedContainer.createForHost(
                containerNative, structApp.getModuleId(), List.of());

        var future = containerNested.runModule("run");
        future.get(30, TimeUnit.SECONDS); // event-driven completion; throws here if the run failed
        assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                sModule + " must complete cleanly on the shared native plane");
    }

    private static String helloModule(String sModule, String tag) {
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        console.print("hello from %s");
                    }
                }
                """.formatted(sModule, tag);
    }
}
