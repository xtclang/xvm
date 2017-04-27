package org.xvm.compiler;


import org.xvm.asm.FileStructure;

import org.xvm.asm.ModuleRepository;
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

    public Compiler(ModuleRepository repos, TypeCompositionStatement module, ErrorListener listener)
        {
        assert repos != null;
        assert module != null;
        assert listener != null;
        assert module.getCategory().getId() == Token.Id.MODULE;

        m_repos  = repos;
        m_module = module;
        m_errs   = listener;
        m_fRoot  = module.getName().equals("ecstasy.xtclang.org");
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the TypeCompositionStatement for the module
     */
    public TypeCompositionStatement getModule()
        {
        return m_module;
        }

    /**
     * @return the ErrorListener that the compiler reports errors to
     */
    public ErrorListener getErrorListener()
        {
        return m_errs;
        }

    /**
     * @return the FileStructure if it has been created
     */
    public FileStructure getFileStructure()
        {
        return m_struct;
        }


    // ----- public API ----------------------------------------------------------------------------

    /**
     * First pass: Create a FileStructure that represents the module, its packages, their classes,
     * their nested classes (recursively), plus the names of properties and methods within each of
     * those.
     * <p/>
     * This method is not permitted to use the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     *
     * @return the initial file structure
     */
    public FileStructure generateInitialFileStructure()
        {
        m_struct = m_module.createModuleStructure(m_errs);
        return m_struct;
        }

    /**
     * Second pass: Add the properties, methods, and the remainder of the classes that are located
     * within the properties and methods to the existing FileStructure.
     * <p/>
     * This method uses the ModuleRepository to obtain type resolution information from other
     * modules. Specifically, type resolution information is the same set of information that was
     * produced by the first pass of this compiler, which allows two different modules with
     * co-dependencies to be jointly compiled.
     * <p/>
     * Any error results are logged to the ErrorListener.
     *
     * @return the FileStructure with the methods and properties added
     */
    public void populateMembers()
        {
        // TODO - this is the next project to do
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
    /**
     * Another property by the same name exists.
     */
    public static final String PROP_DUPLICATE       = "COMPILER-05";
    /**
     * Cannot nest a property.
     */
    public static final String PROP_UNEXPECTED = "COMPILER-04";


    // ----- data members --------------------------------------------------------------------------

    /**
     * The module repository to use.
     */
    private final ModuleRepository m_repos;

    /**
     * The TypeCompositionStatement for the module being compiled. This is an object returned from
     * the Parser, or one assembled from multiple objects returned from the Parser.
     */
    private final TypeCompositionStatement m_module;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_errs;

    /**
     * The FileStructure that this compiler is putting together in a series of passes.
     */
    private FileStructure m_struct;

    private boolean m_fRoot;
    }
