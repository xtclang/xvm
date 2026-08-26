package org.xvm.api;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.tool.Compiler;
import org.xvm.tool.Console;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import org.xvm.util.Severity;

/**
 * Shared fixture for embedding-API tests: locates the built XDK in the checkout and compiles a
 * throwaway module against it. This centralises the build-output discovery that was previously
 * copy-pasted into every runtime test, so there is one honest place documenting that it is a
 * TEST-ONLY locator of gradle build artifacts (not something the embedding API itself does - the
 * real API takes an explicit module path from the caller).
 */
public final class EmbeddingTestSupport {
    /** Collect plenty of diagnostics before capping; a valid test module yields none. */
    private static final int MAX_DIAGNOSTICS = 1000;

    /** Build-output roots that, when present, together form the system module path. */
    private static final List<String> SYSTEM_MODULE_PATHS = List.of(
            "lib_ecstasy/build/xtc/main/lib",
            "javatools_bridge/build/xtc/main/lib",
            "xdk/build/install/xdk/lib",
            "xdk/build/install/xdk/javatools");

    private EmbeddingTestSupport() {
    }

    /**
     * @return the system module repository assembled from whichever build outputs are present, or
     *         null if none are
     */
    public static ModuleRepository systemRepository() {
        var repositories = SYSTEM_MODULE_PATHS.stream()
                .map(EmbeddingTestSupport::repositoryFor)
                .filter(Objects::nonNull)
                .toList();
        return repositories.isEmpty()
                ? null
                : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
    }

    /**
     * @return true iff the compiled core modules the embedding tests need are on disk
     */
    public static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    /**
     * Compile a single-module source into {@code dirOut}, failing the test on serious errors.
     */
    public static void compile(Path dirWork, Path dirOut, String sModule, String sSource)
            throws Exception {
        var source  = dirWork.resolve(sModule + ".x");
        Files.writeString(source, sSource);

        var builder = CompilerOptions.builder()
                .addInputFile(source.toString())
                .setOutputLocation(dirOut.toString());
        for (var path : SYSTEM_MODULE_PATHS) {
            var dir = checkoutFile(path);
            if (dir.isDirectory()) {
                builder.addModulePath(dir.getAbsolutePath());
            }
        }

        var errors = new ErrorList(MAX_DIAGNOSTICS);
        int result = new Compiler(builder.build(), silentConsole(), errors).run();
        if (result != 0 || errors.hasSeriousErrors()) {
            throw new IllegalStateException("compile of " + sModule + " failed: " + errors.getErrors());
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

    private static ModuleRepository repositoryFor(String path) {
        var directory = checkoutFile(path);
        return directory.isDirectory() ? new DirRepository(directory, true) : null;
    }

    /**
     * Resolve a checkout-relative path against the repository root (the nearest ancestor of the
     * working directory that contains a {@code javatools} directory). Test-only: it reaches into
     * gradle build outputs, so it is guarded by {@link #systemModulesAvailable()} at every call
     * site rather than relied upon to always succeed.
     */
    static File checkoutFile(String path) {
        var root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }
}
