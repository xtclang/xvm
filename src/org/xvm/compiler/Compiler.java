package org.xvm.compiler;


import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.ast.TypeCompositionStatement;


/**
 * A module compiler for Ecstasy code.
 * <p/>
 * The compiler is a multi-step state machine. This is the result of the compiler for one module
 * needing to be able to be coordinated with compilers for other modules that are co-dependent,
 * i.e. that have dependencies on each other that need to be jointly resolved.
 *
 * @author cp 2017.04.19
 */
public class Compiler
    {
    // ----- constructors --------------------------------------------------------------------------

    public Compiler(TypeCompositionStatement module, ErrorListener listener)
        {
        assert module != null;
        assert listener != null;
        assert module.getCategory().getId() == Token.Id.MODULE;

        m_module        = module;
        m_errorListener = listener;
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * Create a FileStructure that represents the module, its packages, their classes, their nested
     * classes (recursively), plus the names of properties and methods within each of those.
     * <p/>
     * This method operates without any external dependencies.
     *
     * @return a FileStructure
     */
    FileStructure generateInitialFileStructure()
        {
        FileStructure struct = m_module.createModuleStructure(m_errorListener);
        // TODO ?
        return struct;
        }


    // ----- constants ---------------------------------------------------------

    /**
     * Unknown fatal error.
     */
    public static final String FATAL_ERROR          = "COMPILER-01";
    /**
     * Cannot nest a module.
     */
    public static final String MODULE_UNEXPECTED    = "COMPILER-02";
    /**
     * Cannot nest a package.
     */
    public static final String PACKAGE_UNEXPECTED   = "COMPILER-03";
    /**
     * Cannot nest a class etc.
     */
    public static final String CLASS_UNEXPECTED     = "COMPILER-04";


    // ----- data members --------------------------------------------------------------------------

    /**
     * The Source to parse.
     */
    private final TypeCompositionStatement m_module;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_errorListener;
    }
