package org.xvm.asm;


import java.io.File;

/**
 * Thrown when a repository was explicitly asked for a module that a candidate file should contain,
 * but the file's contents could not be loaded.
 *
 * <p>This is deliberately distinct from a {@code null} result of
 * {@link ModuleRepository#loadModule}: {@code null} means "this repository does not hold that
 * module", while this exception means "the requested module's storage exists and is broken". The
 * old behavior collapsed both cases into {@code null} after printing one line, so a corrupt module
 * file was indistinguishable from a missing module for every caller. Best-effort repository
 * scanning ({@link ModuleRepository#getModuleNames()} and friends) still skips broken candidates
 * without throwing; only requested loads surface the retained cause.
 */
public class ModuleLoadException
        extends RuntimeException {
    /**
     * Construct a ModuleLoadException.
     *
     * @param sModule  the requested module name
     * @param file     the file that should contain the module, but could not be loaded
     * @param cause    the underlying load failure, if it is available in this process
     */
    public ModuleLoadException(String sModule, File file, Throwable cause) {
        super("failed to load module \"" + sModule + "\" from " + file, cause);

        f_sModule = sModule;
        f_file    = file;
    }

    /**
     * @return the requested module name
     */
    public String getModuleName() {
        return f_sModule;
    }

    /**
     * @return the file that should contain the requested module
     */
    public File getFile() {
        return f_file;
    }

    private final String f_sModule;
    private final File   f_file;
}
