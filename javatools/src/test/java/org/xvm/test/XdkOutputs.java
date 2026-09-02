package org.xvm.test;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

/**
 * Where the compiled XDK modules are, told to the tests by the build.
 *
 * <p>Tests used to find this by walking up from the working directory looking for a {@code .git},
 * copied into fifteen test classes. That is unsound rather than merely repetitive: {@code .git} is a
 * <em>file</em> in a linked worktree and absent entirely in a container or an exported source tree,
 * and the walk's failure mode was an {@code assumeTrue} skip - so the suite stayed green while the
 * tests never ran.</p>
 *
 * <p>The build knows where its own outputs are, so it passes {@value #ROOT_PROPERTY}. Nothing here
 * searches the filesystem for a checkout.</p>
 */
public final class XdkOutputs {
    /**
     * System property carrying the composite build root, set for every test task.
     */
    public static final String ROOT_PROPERTY = "xvm.checkout.root";

    /**
     * The build output directories that together hold the system modules.
     */
    private static final List<String> MODULE_DIRS = List.of(
            "lib_ecstasy/build/xtc/main/lib",
            "javatools_bridge/build/xtc/main/lib",
            "xdk/build/install/xdk/lib");

    private XdkOutputs() {}

    /**
     * @return the composite build root, or null when the build did not supply one
     */
    public static Path root() {
        String sRoot = System.getProperty(ROOT_PROPERTY);
        if (sRoot == null || sRoot.isBlank()) {
            return null;
        }
        Path path = Path.of(sRoot);
        return Files.isDirectory(path) ? path : null;
    }

    /**
     * @return a repository over the compiled system modules, or null when they are not built
     */
    public static ModuleRepository systemRepository() {
        Path pathRoot = root();
        if (pathRoot == null) {
            return null;
        }

        var listRepo = new ArrayList<ModuleRepository>(MODULE_DIRS.size());
        for (String sDir : MODULE_DIRS) {
            File dir = pathRoot.resolve(sDir).toFile();
            if (dir.isDirectory()) {
                listRepo.add(new DirRepository(dir, true));
            }
        }

        return switch (listRepo.size()) {
            case 0  -> null;
            case 1  -> listRepo.getFirst();
            // NOT read-through: it would answer a hit in a later repository by cloning the module
            // into the first one, which here is a read-only build output directory
            default -> new LinkedRepository(false, listRepo.toArray(ModuleRepository[]::new));
        };
    }

    /**
     * @return true iff the compiled system modules are available to load
     */
    public static boolean systemModulesAvailable() {
        ModuleRepository repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }
}
