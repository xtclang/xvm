package org.xvm.api;


import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.InjectionKey;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.InjectionSupplier;


public class LspSupport {
    /**
     * Internal constructor
     */
    LspSupport() {
        if (!configured) {
            throw new IllegalStateException("explicit configuration required before use");
        }
    }

    private static boolean configured;
    private static ModuleRepository cfgRepo;

    /**
     * Provide configuration necessary for the underlying Ecstasy tools and libraries.
     */
    public static void configure(ModuleRepository coreRepo) {
        if (configured) {
            throw new IllegalStateException("explicit configuration has already been performed");
        }
        LspSupport.cfgRepo = coreRepo;
    }

    /**
     * Internal singleton implementation.
     */
    private static class Singleton {
        static LspSupport instance = new LspSupport();
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * @return the LspSupport object
     */
    public static LspSupport instance() {
        return Singleton.instance;
    }

    /**
     * @return the native container ConstantPool used by the runtime instance that is instantiated
     *         by this LspSupport
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
    ModuleStructure compile(String source, ModuleRepository input, ErrorListener errs) {
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
    boolean compile(File file, ModuleRepository input, ModuleRepository output, ErrorListener errs) {
        // TODO GG
        return false;
    }

    interface Control {
        boolean running();
        void kill();
        void sendLineInput(String text);
    }

    /**
     * Create a runtime container and execute the provided module.
     *
     * TODO marcus - do you need injection support? what input/output do you need? what control?
     *
     * @param module      the module to execute
     * @param keyboard    (optional) the InputStream representing keyboard input
     * @param console     (optional) the OutputStream for the executing application
     * @param injections  (optional) additional injection support
     * @param errs        (optional) a means for the container to report uncaught exceptions and
     *                    other errors
     *
     * @return a Control object for the running module
     */
    Control run(
            ModuleStructure                      module,
            InputStream                          keyboard,
            OutputStream                         console,
            Map<InjectionKey, InjectionSupplier> injections,
            ErrorListener                        errs) {
        // TODO GG
        return null;
    }

    /**
     * Create a runtime container and execute the specified module.
     *
     * TODO marcus - do you need injection support? what input/output do you need? what control?
     *
     * @param input       the provider of the necessary modules
     * @param moduleName  the module name to execute; must be loadable from "input"
     * @param keyboard    (optional) the InputStream representing keyboard input
     * @param console     (optional) the OutputStream for the executing application
     * @param injections  (optional) additional injection support
     * @param errs        (optional) a means for the container to report uncaught exceptions and
     *                    other errors
     *
     * @return a Control object for the running module
     */
    Control run(
            ModuleRepository                     input,
            String                               moduleName,
            InputStream                          keyboard,
            OutputStream                         console,
            Map<InjectionKey, InjectionSupplier> injections,
            ErrorListener                        errs) {
        // TODO GG
        return null;
    }
}
