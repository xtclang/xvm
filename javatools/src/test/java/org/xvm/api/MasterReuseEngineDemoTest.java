package org.xvm.api;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.tool.Launcher;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A plain Java program (also runnable as tests) demonstrating what MASTER's reuse engine
 * actually supports through the Java API - written to answer the question "is there already a
 * way in master to run consecutive compiles and runs in one VM, or is Runner.x/the platform a
 * coincidence?":
 *
 * <ul>
 *   <li><b>Consecutive compiles</b>: two {@code Launcher.launch} compiler invocations in one
 *       JVM, plain Java API, no process fork. Each compile is its own job; the JVM (and
 *       whatever the tools cache at process level) is the shared engine.</li>
 *   <li><b>Consecutive runs, Regime A</b>: ONE {@link InterpreterConnector} - which means ONE
 *       {@code Runtime} and ONE {@code NativeContainer}, the single bootstrap - then
 *       {@code loadModule/start/invoke0/join} repeated per module. {@code join()} releases the
 *       main container, so each run is a fresh sibling {@code MainContainer} over the same
 *       native plane. This is EXACTLY the reuse regime {@code Runner.x} and the xqiz.it
 *       platform use (their {@code new Container(template, Lightweight, ...)} child containers
 *       are the guest-level face of the same engine), expressed from Java.</li>
 * </ul>
 *
 * What this deliberately does NOT do is create two Connectors (two bootstraps in one JVM) -
 * that is Regime B, the sequential-relaunch shape that crashes on master's first-owner-captured
 * statics and that this branch's hardening enables.
 */
public class MasterReuseEngineDemoTest {
    /**
     * Standalone entry point: {@code java -cp <javatools+test-classes> \
     *   org.xvm.api.MasterReuseEngineDemoTest <xdkLibDir>}
     */
    public static void main(String[] asArg) throws Exception {
        var demo = new MasterReuseEngineDemoTest();
        demo.consecutiveCompilesInOneJvm();
        demo.consecutiveRunsOverOneNativePlane();
        System.out.println("both demos passed");
    }

    /**
     * Two consecutive COMPILES in one JVM through the Java API.
     */
    @Test
    public void consecutiveCompilesInOneJvm() throws IOException {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        Path dirWork = Files.createTempDirectory("reuse-demo");
        Path dirOut  = Files.createDirectory(dirWork.resolve("lib"));

        compileDemoModule(dirWork, dirOut, "ReuseDemoOne");
        compileDemoModule(dirWork, dirOut, "ReuseDemoTwo");

        assertTrue(Files.exists(dirOut.resolve("ReuseDemoOne.xtc")));
        assertTrue(Files.exists(dirOut.resolve("ReuseDemoTwo.xtc")));
    }

    /**
     * Two consecutive RUNS over ONE bootstrap: one Connector = one Runtime + one
     * NativeContainer; each loadModule/start/invoke0/join cycle is a fresh sibling
     * MainContainer sharing the native plane - master's designed reuse engine, from Java.
     */
    @Test
    public void consecutiveRunsOverOneNativePlane() throws Exception {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        Path dirWork = Files.createTempDirectory("reuse-demo-run");
        Path dirOut  = Files.createDirectory(dirWork.resolve("lib"));
        compileDemoModule(dirWork, dirOut, "ReuseDemoOne");
        compileDemoModule(dirWork, dirOut, "ReuseDemoTwo");

        ModuleRepository repository = new LinkedRepository(
                new DirRepository(dirOut.toFile(), true),
                systemRepository());

        // ONE bootstrap: this is the engine both Runner.x and the platform stand on
        var connector = new InterpreterConnector(repository);

        for (String sModule : new String[] {"ReuseDemoOne", "ReuseDemoTwo"}) {
            connector.loadModule(sModule);
            connector.start(null);

            MethodStructure methodRun = connector.findMethods("run").iterator().next();
            connector.invoke0(methodRun);

            assertEquals(0, connector.join(),
                    "module " + sModule + " must complete cleanly on the shared native plane");
        }
    }

    private static void compileDemoModule(Path dirWork, Path dirOut, String sModule)
            throws IOException {
        Path source = dirWork.resolve(sModule + ".x");
        Files.writeString(source, """
                module %s {
                    void run() {
                        @Inject Console console;
                        console.print("hello from %s");
                    }
                }
                """.formatted(sModule, sModule));

        var builder = CompilerOptions.builder()
                .addModulePath(xdkLibDir().getAbsolutePath())
                .addInputFile(source.toString())
                .setOutputLocation(dirOut.toString());
        var dirJavatools = checkoutFile("xdk/build/install/xdk/javatools");
        if (dirJavatools.isDirectory()) {
            builder.addModulePath(dirJavatools.getAbsolutePath());
        }
        CompilerOptions options = builder.build();

        var errs   = new ErrorList(25);
        int result = Launcher.launch(options, new StdoutConsole(), errs);
        assertEquals(0, result, () -> "compile of " + sModule + " failed: " + errs.getErrors());
        assertFalse(errs.hasSeriousErrors(), () -> String.valueOf(errs.getErrors()));
    }

    private static final class StdoutConsole
            implements org.xvm.tool.Console {
        @Override
        public String log(org.xvm.util.Severity sev, String template, Object... params) {
            String sMsg = org.xvm.tool.Console.formatTemplate(template, params);
            System.out.println(sev + ": " + sMsg);
            return sMsg;
        }
    }

    // ----- XDK discovery (same fixture as ArrayViewGuardTest) -----------------------------------

    private static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    private static File xdkLibDir() {
        var directory = checkoutFile("xdk/build/install/xdk/lib");
        if (!directory.isDirectory()) {
            directory = checkoutFile("lib_ecstasy/build/xtc/main/lib");
        }
        return directory;
    }

    private static ModuleRepository systemRepository() {
        var repositories = Stream.of(
                "lib_ecstasy/build/xtc/main/lib",
                "javatools_bridge/build/xtc/main/lib",
                "xdk/build/install/xdk/lib")
                .map(MasterReuseEngineDemoTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();

        return switch (repositories.size()) {
        case 0  -> null;
        case 1  -> repositories.get(0);
        default -> new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
        };
    }

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory()
                ? new DirRepository(directory, true)
                : null;
    }

    private static File checkoutFile(String path) {
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }
}
