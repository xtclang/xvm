package org.xvm.compiler;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;


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
        if (m_stage != Stage.Initial)
            {
            throw new IllegalStateException("Stage=" + m_stage + " (expected: Initial)");
            }

        m_struct = m_module.createModuleStructure(m_errs);
        m_stage = Stage.Registered;
        return m_struct;
        }

    /**
     * Second pass: Resolve all of the globally-visible dependencies and names. This pass does not
     * recurse into methods.
     * <p/>
     * This method is uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     *
     * @return true iff all of the names have been resolved
     */
    public boolean resolveNames()
        {
        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (m_stage == Stage.Resolved)
            {
            return true;
            }

        if (m_stage != Stage.Registered)
            {
            throw new IllegalStateException("Stage=" + m_stage + " (expected: Registered)");
            }

        if (m_listUnresolved == null)
            {
            // first time through, load any module dependencies
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
                        // no error is logged here; the package that imports the module will detect
                        // the error when it is asked to resolve global names; see
                        // TypeCompositionStatement
                        continue;
                        }

                    ModuleStructure structActual = m_repos.loadModule(sModule); // TODO versions etc.
                    structFingerprint.setFingerprintOrigin(structActual);
                    }
                }
            if (fFatal)
                {
                return false;
                }
            }

        // recursively resolve all of the unresolved global names, and if anything couldn't get done
        // in one pass, then store it off in a list to tackle next time
        List<AstNode> listDeferred = new ArrayList<>();
        if (m_listUnresolved == null)
            {
            // first time through: resolve starting from the module, and recurse down
            m_module.resolveNames(listDeferred, m_errs);
            }
        else
            {
            // second through n-th time through: resolve starting from whatever didn't get resolved
            // last time, and recurse down
            for (AstNode node : m_listUnresolved)
                {
                node.resolveNames(listDeferred, m_errs);
                }
            }

        // when there is nothing left deferred, the resolution stage is completed
        boolean fResolved = listDeferred.isEmpty();
        if (fResolved)
            {
            // force the reregistration of constants after the names are resolved to eliminate
            // cruft from earlier passes, such as Void return types
            m_struct.reregisterConstants();

            m_listUnresolved = null;
            m_stage          = Stage.Resolved;
            }
        else
            {
            m_listUnresolved = listDeferred;
            }
        return fResolved;
        }

    /**
     * After a certain number of attempts to resolve names by invoking {@link #resolveNames}, this
     * method will report any unresolved names as fatal errors.
     */
    public void reportUnresolvableNames()
        {
        if (m_stage == Stage.Registered && m_listUnresolved != null && !m_listUnresolved.isEmpty())
            {
            for (AstNode node : m_listUnresolved)
                {
                node.log(m_errs, Severity.FATAL, Compiler.INFINITE_RESOLVE_LOOP,
                        node.getComponent().getIdentityConstant().toString());
                }
            }
        }

    /**
     * This stage actually finally recurses into the methods, compiling their bodies. This phase
     * generates the code ops, but it also builds any nested structures that are found within the
     * methods, including inner classes, lambdas, and so on.
     */
    public void generateCode()
        {
        if (m_stage != Stage.Resolved)
            {
            throw new IllegalStateException("Stage=" + m_stage + " (expected: Resolved)");
            }

        m_module.generateCode(m_errs);
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
    public static final String INNER_SERVICE_NOT_STATIC           = "COMPILER-17";
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
    /**
     * Name collision. For example, anything named "ecstasy" nested under a module, or a property
     * that has the same name as a type parameter or method, etc.
     */
    public static final String NAME_COLLISION                     = "COMPILER-31";
    /**
     * Not a class type.
     */
    public static final String NOT_CLASS_TYPE                     = "COMPILER-32";
    /**
     * Cannot nest a method.
     */
    public static final String METHOD_UNEXPECTED                  = "COMPILER-33";
    /**
     * Cannot nest a typedef.
     */
    public static final String TYPEDEF_UNEXPECTED                 = "COMPILER-34";
    /**
     * Cannot have an annotation here.
     */
    public static final String ANNOTATION_UNEXPECTED              = "COMPILER-35";
    /**
     * Cannot find name within context.
     */
    public static final String NAME_MISSING                       = "COMPILER-36";
    /**
     * Found name within context, but name is ambiguous.
     */
    public static final String NAME_AMBIGUOUS                     = "COMPILER-37";
    /**
     * Cannot find name.
     */
    public static final String NAME_UNRESOLVABLE                  = "COMPILER-38";
    /**
     * Cannot hide name.
     */
    public static final String NAME_UNHIDEABLE                    = "COMPILER-39";
    /**
     * Return is supposed to be void.
     */
    public static final String RETURN_VOID                        = "COMPILER-40";
    /**
     * Return is supposed to be non-void.
     */
    public static final String RETURN_EXPECTED                    = "COMPILER-41";
    /**
     * Return has the wrong number of arguments: {0} expected, {1} found.
     */
    public static final String RETURN_WRONG_COUNT                 = "COMPILER-42";
    /**
     * Type mismatch: {0} expected, {1} found.
     */
    public static final String WRONG_TYPE                         = "COMPILER-43";
    /**
     * Wrong number of values: {0} expected, {1} found.
     */
    public static final String WRONG_TYPE_ARITY                   = "COMPILER-44";
    /**
     * Value of type {0} is out of range: {1}.
     */
    public static final String VALUE_OUT_OF_RANGE                 = "COMPILER-45";
    /**
     * Statement is not reachable.
     */
    public static final String NOT_REACHABLE                      = "COMPILER-46";
    /**
     * Expression does not evaluate to a constant value.
     */
    public static final String CONSTANT_REQUIRED                  = "COMPILER-47";


    // ----- data members --------------------------------------------------------------------------

    /**
     * The stages of compilation.
     */
    public enum Stage {Initial, Registered, Resolved, CodeGen, };

    /**
     * Current compilation stage.
     */
    private Stage m_stage = Stage.Initial;

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

    /**
     * True if this is the compiler for the Ecstasy module itself.
     */
    private boolean m_fRoot;

    /**
     * After the compiler stage reaches Registered, but before it reaches Resolved, this holds a
     * list of AstNode objects that need to be resolved.
     */
    private transient List<AstNode> m_listUnresolved;
    }
