package org.xvm.xdk;


import java.io.File;

import java.util.ArrayList;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

/**
 * The built XDK, as seen by a test running in the xdk project.
 *
 * <p>Tests that need compiled system modules belong here rather than in javatools: javatools is what
 * <em>produces</em> the compiler, so a javatools test can only hope the XDK exists, and the ones that
 * lived there papered over it by walking up the filesystem for a {@code .git} and silently
 * {@code assumeTrue}-skipping when they came up short. In the xdk project {@code installDist} is
 * upstream, so the distribution is simply present and its absence is a build error worth reporting -
 * the same reasoning {@link XdkIntegrationTest} already follows.</p>
 */
public final class BuiltXdk {
    private BuiltXdk() {}

    /**
     * @return the installed distribution directory
     */
    public static File installDir() {
        return new File(System.getProperty("user.dir"), "build/install/xdk");
    }

    /**
     * @return true iff the distribution has been installed
     */
    public static boolean isInstalled() {
        return new File(installDir(), "lib").isDirectory()
            && new File(installDir(), "javatools").isDirectory();
    }

    /**
     * @return a repository over the distribution's system modules
     */
    public static ModuleRepository systemRepository() {
        var listRepo = new ArrayList<ModuleRepository>(2);
        for (String sDir : new String[]{"lib", "javatools"}) {
            File dir = new File(installDir(), sDir);
            if (dir.isDirectory()) {
                listRepo.add(new DirRepository(dir, true));
            }
        }
        return switch (listRepo.size()) {
            case 0  -> null;
            case 1  -> listRepo.getFirst();
            // NOT read-through: it would answer a hit in a later repository by cloning the module
            // into the first, and the distribution directory is not ours to write
            default -> new LinkedRepository(false, listRepo.toArray(ModuleRepository[]::new));
        };
    }

    /**
     * @return true iff the system modules can be loaded from the distribution
     */
    public static boolean systemModulesAvailable() {
        ModuleRepository repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }
}
