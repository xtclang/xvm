package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;

import org.xvm.asm.op.Return_0;


/**
 * Shared fixture for tests that need a live runtime rather than a mock: it locates the compiled
 * system modules in the gradle build outputs and boots a real primordial container over them.
 * <p/>
 * This lives in {@code org.xvm.runtime} because creating a native entry frame needs
 * {@link ServiceContext#createServiceEntryFrame} and {@link ServiceContext.Message}, which are not
 * visible outside the package. Tests in other packages call {@link #entryFrame} from here.
 */
public final class RuntimeTestSupport {
    private RuntimeTestSupport() {
    }

    /**
     * @return a fresh primordial container over the compiled system modules
     */
    public static NativeContainer newContainer() {
        return new NativeContainer(new Runtime(), systemRepository());
    }

    /**
     * Create a native entry frame for the specified service, of the shape a native method receives.
     */
    public static Frame entryFrame(ServiceContext context) {
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

    /**
     * @return true iff the compiled core modules these tests need are on disk
     */
    public static boolean systemModulesAvailable() {
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
                .map(RuntimeTestSupport::repositoryFor)
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

    /**
     * Resolve a checkout-relative path against the repository root, i.e. the nearest ancestor of
     * the working directory that contains a {@code javatools} directory.
     */
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
}
