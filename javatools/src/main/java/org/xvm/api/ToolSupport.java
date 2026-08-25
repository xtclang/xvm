package org.xvm.api;


import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.time.Instant;

import java.util.List;
import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.Version;


/**
 * A Connector-like implementation to support LSP and other tool uses.
 */
public class ToolSupport {
    // ----- internal (construction etc.) ----------------------------------------------------------

    /**
     * Internal constructor
     */
    ToolSupport() {
        if (!configured) {
            throw new IllegalStateException("explicit configuration required before use");
        }
    }

    private static boolean configured;
    private static ModuleRepository cfgRepo;

    /**
     * Internal singleton implementation.
     */
    private static class Singleton {
        static ToolSupport instance = new ToolSupport();
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Provide configuration necessary for the underlying Ecstasy tools and libraries. Must be
     * called exactly one time before any other method.
     *
     * @param coreRepo        the ModuleRepository to load the core libraries from
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources in lieu of the default injector
     *                        for this implementation
     */
    public static void configure(ModuleRepository coreRepo, String customInjector) {
        if (configured) {
            throw new IllegalStateException("explicit configuration has already been performed");
        }
        ToolSupport.cfgRepo = coreRepo;
    }

    /**
     * @return the ToolSupport object
     */
    public static ToolSupport instance() {
        return Singleton.instance;
    }

    /**
     * @return the "native" container (aka "container negative one") ConstantPool used by the
     *         runtime instance that is instantiated by this ToolSupport
     */
    public ConstantPool pool() {
        // TODO GG
        return null;
    }

    /**
     * Compile a module that is in a String.
     *
     * @param source  the source code for an entire module to compile
     * @param input   (optional) the module repository to read any required modules from
     * @param errs    (optional) the ErrorListener to log any compiler messages to
     *
     * @return the resulting ModuleStructure, or null if a compiler error occurred
     */
    public ModuleStructure compile(String source, ModuleRepository input, ErrorListener errs) {
        // TODO GG
        return null;
    }

    /**
     * Compile a module that is in a file or directory.
     *
     * @param file    the location of the module source code on disk, either the module source file
     *                or the directory containing a single .x file and nested contents thereof
     * @param input   (optional) the module repository to read any required modules from
     * @param output  (optional) the module repository to write any compiled modules to
     * @param errs    (optional) the ErrorListener to log any compiler messages to
     *
     * @return true if the compilation succeeded and the result was placed into the output
     */
    public boolean compile(File file, ModuleRepository input, ModuleRepository output, ErrorListener errs) {
        // TODO GG
        return false;
    }

    /**
     * Represents management and monitoring information about a running Ecstasy module.
     */
    public interface Control {
        /**
         * @return true if the module is still running
         */
        boolean running();

        /**
         * @return the Java Instant when the app was started up
         */
        Instant whenStarted();

        /**
         * @return if running() is false, this is the Java Instant when the app stopped running,
         *         otherwise null
         */
        Instant whenStopped();

        /**
         * Stop the app as quickly as possible, and release all of its resources where possible.
         */
        void kill();

        /**
         * @return the Ecstasy Int exit code from the module's run() method, provided as a Java
         *         "Long"; null otherwise
         */
        Long result();
    }

    /**
     * Create a runtime container and execute the provided module.
     *
     * A limited set of injections are made available to the module, including the console, clock,
     * and other "safe" injectable types. The FileSystem is provided as detailed by the "rootDir"
     * parameter.
     *
     * @param module      the module to execute
     * @param console     (optional) the OutputStream for the executing application
     * @param rootDir     (optional) the root directory for the application's file system; null
     *                    indicates a temporary (e.g. in-memory) file system only
     * @param injections  (optional) additional "String" and "String[]" injections
     * @param errs        (optional) a means for the container to report uncaught exceptions and
     *                    other errors
     *
     * @return a Control object for the running module
     */
    public Control run(
            ModuleStructure           module,
            OutputStream              console,
            File                      rootDir,
            Map<String, List<String>> injections,
            ErrorListener             errs) {
        // TODO GG
        return null;
    }

    /**
     * Create a runtime container and execute the specified module.
     *
     * The "customerInjector" option allows the caller to indicate an Ecstasy Injector class that
     * will be loaded into its own container, and provided with the full set of injectable resources
     * that Ecstasy supports, also including any provided String injections; in turn, that
     * implementation provides the injections that will be available to the specified module within
     * its own container.
     *
     * @param input           the ModuleRepository providing any necessary modules
     * @param moduleName      the module name to execute; must be loadable from "input"
     * @param version         (optional) the version of the module to load
     * @param console         (optional) the OutputStream for the executing application
     * @param rootDir         (optional) the root directory for the application's file system; null
     *                        indicates a temporary (e.g. in-memory) file system only
     * @param injections      (optional) additional "String" and "String[]" injections
     * @param customInjector  (optional) "module:class" name of a custom injector implementation to
     *                        use to provide injectable resources; when used, the "rootDir" value is
     *                        ignored
     * @param errs            (optional) a means for the container to report uncaught exceptions and
     *                        other errors
     *
     * @return a Control object for the running module
     */
    public Control run(
            ModuleRepository          input,
            String                    moduleName,
            Version                   version,
            InputStream               keyboard,
            OutputStream              console,
            File                      rootDir,
            Map<String, List<String>> injections,
            String                    customInjector,
            ErrorListener             errs) {
        // TODO GG
        return null;
    }

    /**
     * Informational only: App starting.
     */
    public static final String INFO_STARTED               = "RT-01";
    /**
     * Informational only: App stopped.
     */
    public static final String INFO_STOPPED               = "RT-02";
    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE          = "RT-10";
    /**
     * "%1" - name of missing app module
     * "%2" - version of missing app module
     */
    public static final String ERR_NO_APP_MODULE_VER      = "RT-11";
    /**
     * "%1" - name of app module
     * "%2" - exception (may be null)
     * "%3" - additional description (may be null)
     */
    public static final String ERR_BAD_APP_MODULE         = "RT-12";
    /**
     * "%1" - name of missing module
     */
    public static final String ERR_MISSING_MODULE         = "RT-13";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_CREATE_APP_CONTAINER   = "RT-14";
    /**
     * "%1" - name of injector module
     * "%2" - name of injector class
     * "%3" - exception (may be null)
     * "%4" - additional description (may be null)
     */
    public static final String ERR_CREATE_CUSTOM_INJECTOR = "RT-15";
    /**
     * "%1" - exception
     */
    public static final String ERR_UNHANDLED_EXCEPTION    = "RT-16";
    /**
     * "%1" - exception (may be null)
     * "%2" - additional description (may be null)
     */
    public static final String ERR_INTERNAL               = "RT-99";
}
