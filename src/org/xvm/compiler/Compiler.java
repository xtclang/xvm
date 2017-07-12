package org.xvm.compiler;


import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.asm.ModuleStructure;
import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.TypeCompositionStatement;
import org.xvm.util.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
     * Second pass: Resolve all of the globally-visible dependencies and names. This pass does not
     * recurse into methods.
     * <p/>
     * This method is uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     */
    public void resolveGlobalNames()
        {
        // first, load any module dependencies
        boolean fFatal = false;
        for (String sModule : m_struct.moduleNames())
            {
            if (!sModule.equals(m_struct.getModuleName()))
                {
                ModuleStructure structFingerprint = m_struct.getModule(sModule);
                assert structFingerprint.isFingerprint();
                assert structFingerprint.getFingerprintOrigin() == null;

                // load the module against which the compilation will occur
                if (!m_repos.getModuleNames().contains(sModule))
                    {
                    // no error is logged here; the package that imports the module will detect the
                    // error when it is asked to resolve global names; see TypeCompositionStatement
                    continue;
                    }

                ModuleStructure structActual = m_repos.loadModule(sModule); // TODO versions etc.
                structFingerprint.setFingerprintOrigin(structActual);
                }
            }
        if (fFatal)
            {
            return;
            }

        // second, recursively resolve all of the unresolved global names, and if anything couldn't
        // get done in one pass, then repeat
        int cTries = 0;
        List<AstNode> listTodo = Collections.singletonList(m_module);
        do
            {
            List<AstNode> listDeferred = new ArrayList<>();
            for (AstNode node : listTodo)
                {
                node.resolveGlobalVisibility(listDeferred, m_errs);
                }
            listTodo = listDeferred;
            }
        while (!listTodo.isEmpty() && cTries < 0x3F);
        for (AstNode node : listTodo)
            {
            node.log(m_errs, Severity.FATAL, INFINITE_RESOLVE_LOOP, node.getComponent().getIdentityConstant().toString());
            }
        }

    /**
     * Third pass: Resolve names and structures within methods.
     * <p/>
     * Any error results are logged to the ErrorListener.
     */
    public void resolveRemainder()
        {
        // TODO
        }


    // ----- constants ---------------------------------------------------------

    /**
     * Unknown fatal error.
     */
    public static final String FATAL_ERROR                        = "COMPILER-01";
    /**
     * Cannot nest a module.
     */
    public static final String MODULE_UNEXPECTED                  = "COMPILER-02";
    /**
     * Cannot nest a package.
     */
    public static final String PACKAGE_UNEXPECTED                 = "COMPILER-03";
    /**
     * Cannot nest a class etc.
     */
    public static final String CLASS_UNEXPECTED                   = "COMPILER-04";
    /**
     * Another property by the same name exists.
     */
    public static final String PROP_DUPLICATE                     = "COMPILER-05";
    /**
     * Cannot nest a property.
     */
    public static final String PROP_UNEXPECTED                    = "COMPILER-06";
    /**
     * Illegal module name.
     */
    public static final String MODULE_BAD_NAME                    = "COMPILER-07";
    /**
     * Duplicate modifier.
     */
    public static final String DUPLICATE_MODIFIER                 = "COMPILER-08";
    /**
     * Illegal modifier.
     */
    public static final String ILLEGAL_MODIFIER                   = "COMPILER-09";
    /**
     * Conflicting modifier.
     */
    public static final String CONFLICTING_MODIFIER               = "COMPILER-10";
    /**
     * More than one "extends" clause.
     */
    public static final String MULTIPLE_EXTEND_CLAUSES            = "COMPILER-11";
    /**
     * Illegal / unexpected type parameters.
     */
    public static final String TYPE_PARAMS_UNEXPECTED             = "COMPILER-12";
    /**
     * Illegal / unexpected constructor parameters.
     */
    public static final String CONSTRUCTOR_PARAMS_UNEXPECTED      = "COMPILER-13";
    /**
     * Illegal / unexpected constructor parameters.
     */
    public static final String CONSTRUCTOR_PARAM_DEFAULT_REQUIRED = "COMPILER-14";
    /**
     * Unexpected keyword.
     */
    public static final String KEYWORD_UNEXPECTED                 = "COMPILER-15";
    /**
     * Inner const class must be declared static if its outer class is not a const.
     */
    public static final String INNER_CONST_NOT_STATIC             = "COMPILER-16";
    /**
     * Inner service class must be declared static if its outer class is not a const or service.
     */
    public static final String INNER_SERVIC_NOT_STATIC            = "COMPILER-17";
    /**
     * Wrong number of type parameter values.
     */
    public static final String TYPE_PARAMS_MISMATCH               = "COMPILER-18";
    /**
     * Type parameter name is a duplicate.
     */
    public static final String DUPLICATE_TYPE_PARAM               = "COMPILER-19";
    /**
     * More than one "import" clause.
     */
    public static final String MULTIPLE_IMPORT_CLAUSES            = "COMPILER-20";
    /**
     * More than one "into" clause.
     */
    public static final String MULTIPLE_INTO_CLAUSES              = "COMPILER-21";
    /**
     * Package cannot have both body and "import" clause.
     */
    public static final String IMPURE_MODULE_IMPORT               = "COMPILER-22";
    /**
     * A conditional is not allowed on this structure.
     */
    public static final String CONDITIONAL_NOT_ALLOWED            = "COMPILER-23";
    /**
     * Cannot find a module.
     */
    public static final String MODULE_MISSING                     = "COMPILER-24";
    /**
     * Conflicting version clauses.
     */
    public static final String CONFLICTING_VERSIONS               = "COMPILER-25";
    /**
     * Conflicting import composition when importing one's own module.
     */
    public static final String ILLEGAL_SELF_IMPORT                = "COMPILER-26";
    /**
     * Illegal link-time conditional.
     */
    public static final String ILLEGAL_CONDITIONAL                = "COMPILER-27";
    /**
     * Duplicate import with the same alias.
     */
    public static final String DUPLICATE_IMPORT                   = "COMPILER-28";
    /**
     * Import cannot be conditional; condition ignored.
     */
    public static final String CONDITIONAL_IMPORT                 = "COMPILER-29";
    /**
     * Unresolvable names.
     */
    public static final String INFINITE_RESOLVE_LOOP              = "COMPILER-30";


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
