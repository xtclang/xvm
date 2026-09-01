package org.xvm.asm;


import java.io.File;

import java.util.List;
import java.util.Objects;

import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

/**
 * Shared fixture for the display-purity gates: locates the compiled XDK system modules in the
 * gradle build outputs and stands up a real {@link NativeContainer} over them.
 *
 * <p>This is a TEST-ONLY locator of build artifacts. The purity gates need a fully realized type
 * system (a live {@code ConstantPool} with real {@code TypeInfo}s in it) because the display
 * side effects they hunt for - pool interning, lazy forcing, resolution write-back - only happen
 * against real content.</p>
 */
public final class DisplayPurityFixture {
    /** Build-output roots that, when present, together form the system module path. */
    private static final List<String> SYSTEM_MODULE_PATHS = List.of(
            "lib_ecstasy/build/xtc/main/lib",
            "javatools_bridge/build/xtc/main/lib",
            "xdk/build/install/xdk/lib",
            "xdk/build/install/xdk/javatools");

    private DisplayPurityFixture() {
    }

    /**
     * @return the system module repository assembled from whichever build outputs are present, or
     *         null if none are
     */
    public static ModuleRepository systemRepository() {
        var repositories = SYSTEM_MODULE_PATHS.stream()
                .map(DisplayPurityFixture::repositoryFor)
                .filter(Objects::nonNull)
                .toList();
        return repositories.isEmpty()
                ? null
                : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
    }

    /**
     * @return true iff the compiled core modules these tests need are on disk
     */
    public static boolean systemModulesAvailable() {
        var repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    /**
     * @return a started runtime; the caller owns it and must {@code shutdownXVM()} it
     */
    public static Runtime startRuntime() {
        var runtime = new Runtime();
        runtime.start();
        return runtime;
    }

    /**
     * @return the shared {@link ConstantPool} of a freshly created container zero
     */
    public static ConstantPool nativePool(Runtime runtime) {
        return new NativeContainer(runtime, systemRepository()).getConstantPool();
    }

    /** tests run with the working directory at either the repo root or the javatools project */
    private static final List<String> ROOTS = List.of("", "../");

    private static ModuleRepository repositoryFor(String sPath) {
        return ROOTS.stream()
                .map(sPrefix -> new File(sPrefix + sPath))
                .filter(File::isDirectory)
                .<ModuleRepository>map(dir -> new DirRepository(dir, true))
                .findFirst()
                .orElse(null);
    }
}
