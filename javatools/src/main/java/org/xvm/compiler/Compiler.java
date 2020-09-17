package org.xvm.compiler;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;

import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.TypeCompositionStatement;

import org.xvm.util.Severity;


/**
 * A module compiler for Ecstasy code.
 * <p/>
 * The compiler is a multi-step state machine. This design is the result of the compiler for one
 * module needing to be able to be coordinated with compilers for other modules that are
 * co-dependent, i.e. that have dependencies on each other that need to be jointly resolved.
 */
public class Compiler
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a module compiler.
     *
     * @param repos   the module repository
     * @param stmtModule  the statement representing all of the code in the module
     * @param errs    the error list to log any errors to during the various phases of compilation
     */
    public Compiler(ModuleRepository repos, TypeCompositionStatement stmtModule, ErrorList errs)
        {
        if (repos == null)
            {
            throw new IllegalArgumentException("Repository required");
            }
        if (stmtModule == null)
            {
            throw new IllegalArgumentException("AST node for module required");
            }
        if (stmtModule.getCategory().getId() != Token.Id.MODULE)
            {
            throw new IllegalArgumentException("AST node for module is not a module statement");
            }
        if (errs == null)
            {
            throw new IllegalArgumentException("ErrorList required");
            }

        m_repos      = repos;
        m_stmtModule = stmtModule;
        m_errs       = errs;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the ModuleRepository that was supplied to the compiler
     */
    public ModuleRepository getRepository()
        {
        validateCompiler();
        return m_repos;
        }

    /**
     * @return the TypeCompositionStatement for the module
     */
    public TypeCompositionStatement getModuleStatement()
        {
        validateCompiler();
        return m_stmtModule;
        }

    /**
     * @return the ErrorList that the compiler reports errors to
     */
    public ErrorList getErrorListener()
        {
        validateCompiler();
        return m_errs;
        }

    /**
     * @return the FileStructure if it has been created
     */
    public FileStructure getFileStructure()
        {
        validateCompiler();
        return m_structFile;
        }

    /**
     * @return the current stage of the compiler
     */
    public Stage getStage()
        {
        return m_stage;
        }

    /**
     * Test if the compiler has reached the specified stage.
     *
     * @param stage  the compiler stage to test for
     *
     * @return true if the compiler has already reached or passed the specified stage
     */
    public boolean alreadyReached(Stage stage)
        {
        validateCompiler();
        assert stage != null;
        return getStage().compareTo(stage) >= 0;
        }

    /**
     * @return true if the compiler has decided to abort the process
     */
    public boolean isAbortDesired()
        {
        return m_errs.isAbortDesired();
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
        validateCompiler();

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (getStage() == Stage.Initial)
            {
            setStage(Stage.Registering);

            StageMgr mgr = new StageMgr(m_stmtModule, Stage.Registered, m_errs);
            if (!mgr.processComplete())
                {
                throw new CompilerException("failed to create module");
                }
            m_structFile = m_stmtModule.getComponent().getFileStructure();
            m_structFile.setErrorListener(m_errs);
            setStage(Stage.Registered);
            }

        return m_structFile;
        }

    /**
     * Second pass: Link the modules together based on their declared dependencies.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     */
    public void linkModules()
        {
        validateCompiler();
        ensureReached(Stage.Registered);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Loaded))
            {
            return;
            }

        try (var x = ConstantPool.withPool(m_structFile.getConstantPool()))
            {
            // first time through, load any module dependencies
            setStage(Stage.Loading);
            m_structFile.linkModules(m_repos, false);
            }

        setStage(Stage.Loaded);
        }

    /**
     * Third pass: Resolve all of the globally-visible dependencies and names. This pass does not
     * recurse into methods.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean resolveNames()
        {
        validateCompiler();
        ensureReached(Stage.Registered);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Resolved))
            {
            return true;
            }

        try (var x = ConstantPool.withPool(m_structFile.getConstantPool()))
            {
            // recursively resolve all of the unresolved global names, and if anything couldn't get done
            // in one pass, then store it off in a list to tackle next time
            if (!alreadyReached(Stage.Resolving))
                {
                // first time through: resolve starting from the module, and recurse down
                setStage(Stage.Resolving);
                m_mgr = new StageMgr(m_stmtModule, Stage.Resolved, m_errs);
                }

            if (m_mgr.processComplete())
                {
                setStage(Stage.Resolved);
                }
            }

        return m_mgr.isComplete();
        }

    /**
     * Fourth pass: Resolve all types and constants. This does recurse to the full depth of the AST
     * tree.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean validateExpressions()
        {
        validateCompiler();
        ensureReached(Stage.Resolved);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Validated))
            {
            return true;
            }

        try (var x = ConstantPool.withPool(m_structFile.getConstantPool()))
            {
            // recursively resolve all of the unresolved global names, and if anything couldn't get done
            // in one pass, the manager will keep track of what remains to be done
            if (!alreadyReached(Stage.Validating))
                {
                // first time through: resolve starting from the module, and recurse down
                setStage(Stage.Validating);
                m_mgr = new StageMgr(m_stmtModule, Stage.Validated, m_errs);
                }

            if (m_mgr.processComplete())
                {
                setStage(Stage.Validated);
                }
            }

        return m_mgr.isComplete();
        }

    /**
     * This stage finishes the compilation by emitting any necessary code and any remaining
     * structures.
     * <p/>
     * This method uses the ModuleRepository.
     * <p/>
     * Any error results are logged to the ErrorListener.
     * <p/>
     * The caller is responsible for calling this method until it returns true.
     *
     * @return true iff the pass is complete; false indicates that this method MUST be called again
     */
    public boolean generateCode()
        {
        validateCompiler();
        ensureReached(Stage.Validated);

        // idempotent: allow this to be called more than necessary without any errors/side-effects
        if (alreadyReached(Stage.Emitted))
            {
            return true;
            }

        try (var x = ConstantPool.withPool(m_structFile.getConstantPool()))
            {
            // recursively resolve all of the unresolved global names, and if anything couldn't get done
            // in one pass, then store it off in a list to tackle next time
            if (!alreadyReached(Stage.Emitting))
                {
                // first time through: resolve starting from the module, and recurse down
                setStage(Stage.Emitting);
                m_mgr = new StageMgr(m_stmtModule, Stage.Emitted, m_errs);
                }

            if (m_mgr.processComplete())
                {
                setStage(Stage.Emitted);

                if (m_errs.getSeverity().compareTo(Severity.ERROR) < 0)
                    {
                    // "purge" the constant pool and do a final validation on the entire module structure
                    m_structFile.reregisterConstants(true);
                    m_structFile.validate(m_errs);
                    m_structFile.setErrorListener(null);
                    }
                }
            }

        return m_mgr.isComplete();
        }

    /**
     * After a certain number of attempts to resolve names by invoking {@link #resolveNames}, this
     * method will report any unresolved names as fatal errors.
     */
    public void logRemainingDeferredAsErrors()
        {
        if (!m_errs.hasSeriousErrors())
            {
            m_mgr.logDeferredAsErrors(m_errs);
            }
        }

    /**
     * Discard the compiler. This invalidates the compiler; any further attempts to use the compiler
     * will result in an exception.
     */
    public void invalidate()
        {
        setStage(Stage.Discarded);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Compiler (Module=" + m_stmtModule.getName() + ", Stage=" + getStage() + ")";
        }


    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Verify that the compiler has not been invalidated.
     *
     * @throws IllegalStateException  if the compiler has been invalidated
     */
    private void validateCompiler()
        {
        if (getStage() == Stage.Discarded)
            {
            throw new IllegalStateException();
            }
        }

    /**
     * Verify that the compiler has reached the specified stage.
     *
     * @param stage  the stage that the compiler must have already reached
     *
     * @throws IllegalStateException  if the compiler has not reached the specified stage
     */
    private void ensureReached(Stage stage)
        {
        if (!alreadyReached(stage))
            {
            throw new IllegalStateException("Stage=" + getStage() + " (expected: " + stage + ")");
            }
        }

    /**
     * Update the stage to the specified stage, if the specified stage is later than the current
     * stage.
     *
     * @param stage  the suggested stage
     */
    private void setStage(Stage stage)
        {
        // stage is a "one way" attribute
        if (stage != null && stage.compareTo(m_stage) > 0)
            {
            m_stage = stage;
            }
        }


    // ----- data members --------------------------------------------------------------------------

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
    private TypeCompositionStatement m_stmtModule;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorList m_errs;

    /**
     * The FileStructure that this compiler is putting together in a series of passes.
     */
    private FileStructure m_structFile;

    /**
     * Within a compiler stage that may not complete in a single pass, a manager is responsible for
     * getting all of the nodes to complete that stage.
     */
    private StageMgr m_mgr;


    // ----- inner class: Stage enumeration --------------------------------------------------------

    /**
     * The stages of compilation.
     */
    public enum Stage
        {
        Initial,
        Registering,
        Registered,
        Loading,
        Loaded,
        Resolving,
        Resolved,
        Validating,
        Validated,
        Emitting,
        Emitted,
        Discarded;

        /**
         * @return true if this stage is a "target-able" stage, i.e. a stage that a node can be
         *         asked to process towards
         */
        public boolean isTargetable()
            {
            ensureValid();

            // the even ordinals are targets
            int n = ordinal();
            return (n & 0x1) == 0 && n > 0;
            }

        /**
         * @return true if this stage is a intermediate stage, i.e. indicating that a node is in
         *         the process of moving towards a target-able stage
         */
        public boolean isTransition()
            {
            ensureValid();

            // the odd ordinals are intermediates
            return (ordinal() & 0x1) == 1;
            }

        /**
         * @return the transition stage related to this stage
         */
        public Stage getTransitionStage()
            {
            ensureValid();
            return isTransition()
                    ? this
                    : prev();
            }

        /**
         * Determine if this stage is at least as far along as that stage.
         *
         * @param that  another Stage
         *
         * @return true iff this Stage is at least as advanced as that stage
         */
        public boolean isAtLeast(Stage that)
            {
            ensureValid();
            return this.compareTo(that) >= 0;
            }

        /**
         * Make sure that the stage is not Discarded.
         */
        public void ensureValid()
            {
            if (this == Discarded)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * @return the Stage that comes before this Stage
         */
        public Stage prev()
            {
            ensureValid();
            return Stage.valueOf(this.ordinal() - 1);
            }

        /**
         * @return the Stage that comes after this Stage
         */
        public Stage next()
            {
            ensureValid();
            return Stage.valueOf(this.ordinal() + 1);
            }

        /**
         * @return the first "target-able" Stage that comes before this Stage
         */
        public Stage prevTarget()
            {
            Stage that = prev();
            while (!that.isTargetable())
                {
                that = that.prev();
                }
            return that;
            }

        /**
         * @return the first "target-able" Stage that comes after this Stage
         */
        public Stage nextTarget()
            {
            Stage that = next();
            while (!that.isTargetable())
                {
                that = that.next();
                }
            return that;
            }

        /**
         * Look up a Stage enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Stage enum for the specified ordinal
         */
        public static Stage valueOf(int i)
            {
            if (i >= 0 && i < STAGES.length)
                {
                return STAGES[i];
                }

            throw new IllegalArgumentException("no such stage ordinal: " + i);
            }

        /**
         * All of the Stage enums.
         */
        private static final Stage[] STAGES = Stage.values();
        }


    // ----- compiler errors -----------------------------------------------------------------------

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
     * Property {0} is a duplicate.
     */
    public static final String PROP_DUPLICATE                     = "COMPILER-05";
    /**
     * Property {0} cannot be nested under {1}.
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
     * Validator parameters are not allowed.
     */
    public static final String VALIDATOR_PARAMS_UNEXPECTED        = "COMPILER-13";
    /**
     * Constructor parameters must have default values.
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
     * Type parameter name {0} is a duplicate.
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
     * Not a class type: "{0}".
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
     * Could not find name "{0}" within "{1}".
     */
    public static final String NAME_MISSING                       = "COMPILER-36";
    /**
     * Name "{0}" is ambiguous.
     */
    public static final String NAME_AMBIGUOUS                     = "COMPILER-37";
    /**
     * Name "{0}" is unresolvable..
     */
    public static final String NAME_UNRESOLVABLE                  = "COMPILER-38";
    /**
     * Name "{0}" is unhideable; attempt to hide "{1}" is an error.
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
    /**
     * A value is required.
     */
    public static final String VALUE_REQUIRED                     = "COMPILER-48";
    /**
     * Return is missing.
     */
    public static final String RETURN_REQUIRED                    = "COMPILER-49";
    /**
     * Invalid operation.
     */
    public static final String INVALID_OPERATION                  = "COMPILER-50";
    /**
     * Variable {0} is already defined.
     */
    public static final String VAR_DEFINED                        = "COMPILER-51";
    /**
     * There is no "this".
     */
    public static final String NO_THIS                            = "COMPILER-52";
    /**
     * There is no "super".
     */
    public static final String NO_SUPER                           = "COMPILER-53";
    /**
     * Unexpected redundant return type information.
     */
    public static final String UNEXPECTED_REDUNDANT_RETURNS       = "COMPILER-54";
    /**
     * Method or function type requires complete parameter and return type information.
     */
    public static final String MISSING_PARAM_INFORMATION          = "COMPILER-55";
    /**
     * Could not find a matching method or function "{0}" for type "{1}".
     */
    public static final String MISSING_METHOD                     = "COMPILER-56";
    /**
     * Could not find an "outer this" named "{0}".
     */
    public static final String MISSING_RELATIVE                   = "COMPILER-57";
    /**
     * Unexpected method name "{0}" encountered.
     */
    public static final String UNEXPECTED_METHOD_NAME             = "COMPILER-58";
    /**
     * The "this." prefix must be followed by a parent class or parent property identity.
     */
    public static final String INVALID_OUTER_THIS                 = "COMPILER-59";
    /**
     * Because a previous argument specified a parameter name, the argument {0} must specify a parameter name.
     */
    public static final String ARG_NAME_REQUIRED                  = "COMPILER-60";
    /**
     * Variable declaration cannot use conditional assignment.
     */
    public static final String VAR_DECL_COND_ASN_ILLEGAL          = "COMPILER-61";
    /**
     * No-parameter constructor required for {0}.
     */
    public static final String DEFAULT_CONSTRUCTOR_REQUIRED       = "COMPILER-62";
    /**
     * Signature {0} is ambiguous.
     */
    public static final String SIGNATURE_AMBIGUOUS                = "COMPILER-63";
    /**
     * Type {0} has more than one default value for the type.
     */
    public static final String DUPLICATE_DEFAULT_VALUE            = "COMPILER-64";
    /**
     * Could not find a matching constructor for type "{0}".
     */
    public static final String MISSING_CONSTRUCTOR                = "COMPILER-65";
    /**
     * Tuple type has an unexpected number of field types; {0} expected, {1} found.
     */
    public static final String TUPLE_TYPE_WRONG_ARITY             = "COMPILER-66";
    /**
     * Expression yields the type "{1}" that does not support the "{0}" operator.
     */
    public static final String MISSING_OPERATOR                   = "COMPILER-67";
    /**
     * Expression yields the type "{1}" that does not support the "{0}" operator with the {2} specified parameters.
     */
    public static final String MISSING_OPERATOR_SIGNATURE         = "COMPILER-68";
    /**
     * The use of the "{0}" operator does not resolve to a single, unambiguous operator implementation on the type "{1}".
     */
    public static final String AMBIGUOUS_OPERATOR_SIGNATURE       = "COMPILER-69";
    /**
     * The expression cannot be assigned to.
     */
    public static final String ASSIGNABLE_REQUIRED                = "COMPILER-70";
    /**
     * The left-hand-side of the Elvis expression is not nullable.
     */
    public static final String ELVIS_NOT_NULLABLE                 = "COMPILER-71";
    /**
     * The left-hand-side of the Elvis expression is only nullable.
     */
    public static final String ELVIS_ONLY_NULLABLE                = "COMPILER-72";
    /**
     * Short-circuiting expressions are not allowed in this context.
     */
    public static final String SHORT_CIRCUIT_ILLEGAL              = "COMPILER-73";
    /**
     * The expression on the left-hand-side of the colon does not have the potential to use the expression on the right-hand-side.
     */
    public static final String SHORT_CIRCUIT_REQUIRED             = "COMPILER-74";
    /**
     * A "switch" can only contain one "default" statement.
     */
    public static final String SWITCH_DEFAULT_DUPLICATE           = "COMPILER-75";
    /**
     * A "switch" expression must contain a "default" statement.
     */
    public static final String SWITCH_DEFAULT_REQUIRED            = "COMPILER-76";
    /**
     * The "switch" contains more than one "case" statement for the value: {0}.
     */
    public static final String SWITCH_CASE_DUPLICATE              = "COMPILER-77";
    /**
     * A "switch" requires a constant value for the "case" statement.
     */
    public static final String SWITCH_CASE_CONSTANT_REQUIRED      = "COMPILER-78";
    /**
     * A "switch" must begin with a "case" statement.
     */
    public static final String SWITCH_CASE_EXPECTED               = "COMPILER-79";
    /**
     * A "switch" expression cannot end with a "case" statement.
     */
    public static final String SWITCH_CASE_DANGLING               = "COMPILER-80";
    /**
     * The variable {0} is not definitely assigned.
     */
    public static final String VAR_UNASSIGNED                     = "COMPILER-81";
    /**
     * The variable {0} cannot be assigned to.
     */
    public static final String VAR_ASSIGNMENT_ILLEGAL             = "COMPILER-82";
    /**
     * Name required.
     */
    public static final String NAME_REQUIRED                      = "COMPILER-83";
    /**
     * Wrong number of arguments: {0} expected, {1} found.
     */
    public static final String ARGUMENT_WRONG_COUNT               = "COMPILER-84";
    /**
     * Parameter name is a duplicate: {0}.
     */
    public static final String DUPLICATE_PARAMETER                = "COMPILER-85";
    /**
     * Parameter types must be specified.
     */
    public static final String PARAMETER_TYPES_REQUIRED           = "COMPILER-86";
    /**
     * Auto-narrowing override ('!') is not allowed.
     */
    public static final String AUTO_NARROWING_ILLEGAL             = "COMPILER-87";
    /**
     * Could not find the specified label "{0}".
     */
    public static final String MISSING_GOTO_LABEL                 = "COMPILER-88";
    /**
     * Could not find an enclosing "for", "do", "while", or "switch" statement.
     */
    public static final String MISSING_GOTO_TARGET                = "COMPILER-89";
    /**
     * A "continue" statement can only be applied to a "for", "do", "while", or "switch" statement.
     */
    public static final String ILLEGAL_CONTINUE_TARGET            = "COMPILER-90";
    /**
     * The expression type is not nullable: "{0}".
     */
    public static final String EXPRESSION_NOT_NULLABLE            = "COMPILER-91";
    /**
     * The types "{0}" and "{1}" are not comparable.
     */
    public static final String TYPES_NOT_COMPARABLE               = "COMPILER-92";
    /**
     * A duplicate name is used for a label: "{0}"
     */
    public static final String DUPLICATE_LABEL                    = "COMPILER-93";
    /**
     * The specified label variable "{0}" is not available for label "{1}".
     */
    public static final String LABEL_VARIABLE_ILLEGAL             = "COMPILER-94";
    /**
     * Unexpected number of assignments; minimum is {0} and maximum is {1}.
     */
    public static final String INVALID_LVALUE_COUNT               = "COMPILER-95";
    /**
     * Index value {0} out-of-range; must be between {1} and {2} (inclusive).
     */
    public static final String INVALID_INDEX                      = "COMPILER-95";
    /**
     * The anonymous inner class is of an invalid type.
     */
    public static final String INVALID_ANON_CLASS_TYPE            = "COMPILER-96";
    /**
     * Anonymous inner class cannot extend both {0} and {1}.
     */
    public static final String ANON_CLASS_EXTENDS_MULTI           = "COMPILER-97";
    /**
     * Anonymous inner class cannot extend a class of the {0} category.
     */
    public static final String ANON_CLASS_EXTENDS_ILLEGAL         = "COMPILER-98";
    /**
     * Anonymous inner class is declared such that it must be both mutable and immutable.
     */
    public static final String ANON_CLASS_MUTABILITY_CONFUSED     = "COMPILER-99";
    /**
     * An anonymous inner class may not specify an intersection type.
     */
    public static final String ANON_CLASS_EXTENDS_INTERSECTION    = "COMPILER-100";
    /**
     * The initialization of the property {0} is implied by a constructor parameter, but the
     * property does not exist or not settable.
     */
    public static final String IMPLICIT_PROP_MISSING              = "COMPILER-101";
    /**
     * The initialization of the property {0} is implied by a constructor parameter, but the
     * property type does not match; the expected type is {1} but the property is of type {2}.
     */
    public static final String IMPLICIT_PROP_WRONG_TYPE           = "COMPILER-102";
    /**
     * The implicit super class constructor from {0} does not exist on the class {1}.
     */
    public static final String IMPLICIT_SUPER_CONSTRUCTOR_MISSING = "COMPILER-103";
    /**
     * The "super" function has been used as if it were a normal object reference;
     * it is normally used as a "super(...)" function call.
     */
    public static final String INVALID_SUPER_REFERENCE            = "COMPILER-104";
    /**
     * The import name {0} is the same as the name of an existing variable.
     */
    public static final String IMPORT_NAME_COLLISION              = "COMPILER-105";
    /**
     * The import {0} does not refer to an identity.
     */
    public static final String IMPORT_NOT_IDENTITY                = "COMPILER-106";
    /**
     * A "this" reference is required to access the {0} property of the {1} type.
     */
    public static final String NO_THIS_PROPERTY                   = "COMPILER-107";
    /**
     * A "this" reference is required to access the {0} method of the {1} type.
     */
    public static final String NO_THIS_METHOD                     = "COMPILER-108";
    /**
     * Missing @Override annotation on {0}: This virtual child has a super virtual child.
     */
    public static final String VIRTUAL_CHILD_OVERRIDE_MISSING     = "COMPILER-109";
    /**
     * Invalid @Override annotation on {0}: This virtual child does not have a super virtual child.
     */
    public static final String VIRTUAL_CHILD_OVERRIDE_ILLEGAL     = "COMPILER-110";
    /**
     * Illegal "extends" clause on {0}: This virtual child has a super virtual child.
     */
    public static final String VIRTUAL_CHILD_EXTENDS_ILLEGAL      = "COMPILER-111";
    /**
     * Illegal "extends" clause on interface {0}: A super virtual child class exists with the same name.
     */
    public static final String VIRTUAL_CHILD_EXTENDS_CLASS        = "COMPILER-112";
    /**
     * Illegal explicit "extends" clause on {0}: The virtual child super is implicit.
     */
    public static final String VIRTUAL_CHILD_EXTENDS_IMPLICIT     = "COMPILER-113";
    /**
     * A super virtual child interface exists of the same name {0}, and a class may not extend an interface.
     */
    public static final String VIRTUAL_CHILD_EXTENDS_INTERFACE    = "COMPILER-114";
    /**
     * Multiple super virtual child classes exists of the same name {0}, and a class may not extend multiple classes.
     */
    public static final String VIRTUAL_CHILD_EXTENDS_MULTIPLE     = "COMPILER-115";
    /**
     * The "case" statement has an illegal number of values.
     */
    public static final String SWITCH_CASE_ILLEGAL_ARITY          = "COMPILER-116";
    /**
     * Each "case" group in a "switch" must end with a break or continue (or other non-completing
     * statement).
     */
    public static final String SWITCH_BREAK_OR_CONTINUE_EXPECTED  = "COMPILER-117";
    /**
     * The "catch" clause for {0} is unreachable because {1} was caught by a previous "catch" clause.
     */
    public static final String CATCH_TYPE_ALREADY_CAUGHT          = "COMPILER-118";
    /**
     * No access can be specified for the static property {0} in the method {1}.
     */
    public static final String STATIC_PROP_IN_METHOD_HAS_ACCESS   = "COMPILER-119";
    /**
     * Only private access can be specified for the property {0} in the method {1}.
     */
    public static final String PROP_IN_METHOD_NOT_PRIVATE         = "COMPILER-120";
    /**
     * The "set" access must not be specified on the static property {0} on {1}.
     */
    public static final String STATIC_PROP_HAS_SETTER_ACCESS      = "COMPILER-121";
    /**
     * The "set" access is more accessible than the "get" access for property {0} on {1}.
     */
    public static final String PROP_SETTER_ACCESS_TOO_ACCESSIBLE  = "COMPILER-122";
    /**
     * Type {0} doesn't have a default value. Use "new Array<Element>(...)" instead.
     */
    public static final String NO_DEFAULT_VALUE                   = "COMPILER-123";
    /**
     * Name is reserved: {0}.
     */
    public static final String NAME_RESERVED                      = "COMPILER-124";
    /**
     * Function is not allowed: {0}.
     */
    public static final String FUNCTION_NOT_ALLOWED               = "COMPILER-125";
    /**
     * Function cannot be abstract: {0}.
     */
    public static final String FUNCTION_BODY_MISSING              = "COMPILER-126";
    /**
     * Abstract function cannot be called directly: {0}.
     */
    public static final String ILLEGAL_FUNKY_CALL                 = "COMPILER-127";
    /**
     * The condition for a "while" loop must not evaluate to a constant "False".
     */
    public static final String ILLEGAL_WHILE_CONDITION            = "COMPILER-128";
    /**
     * The target type {0} must be parameterized to call method {1}.
     */
    public static final String ILLEGAL_NAKED_TYPE_INVOCATION      = "COMPILER-129";
    /**
     * The short-circuited expression is always "Null".
     */
    public static final String SHORT_CIRCUIT_ALWAYS_NULL          = "COMPILER-130";
    /**
     * The delegation property {0} does not exist.
     */
    public static final String DELEGATE_PROP_MISSING              = "COMPILER-131";
    /**
     * The delegation type for the property {0} does not match; the expected type is {1},
     * but the property is of type {2}.
     */
    public static final String DELEGATE_PROP_WRONG_TYPE           = "COMPILER-132";
    /**
     * Method {0} already exists.
     */
    public static final String DUPLICATE_METHOD                   = "COMPILER-133";
    /**
     * The evaluating expression {0} has a type of "ecstasy.Type"; it cannot match type {1}.
     */
    public static final String NOT_TYPE_OF_TYPE                   = "COMPILER-134";
    /**
     * A type is expected, but a property identity for a formal type was encountered.
     */
    public static final String INVALID_FORMAL_TYPE_IDENTITY       = "COMPILER-135";
    /**
     * Dynamic type parameters are not supported.
     */
    public static final String UNSUPPORTED_DYNAMIC_TYPE_PARAMS    = "COMPILER-136";
    /**
     * The evaluating expression {0} has a type of "ecstasy.Type"; it always matches type {1}.
     */
    public static final String TYPE_MATCHES_ALWAYS                = "COMPILER-137";
    /**
     * Possible name collision: an attempt to use function {0} defined at {1} as an invocation target.
     */
    public static final String SUSPICIOUS_FUNCTION_USE            = "COMPILER-138";
    /**
     * Illegal usage of the method {0} with a conditional return type.
     */
    public static final String CONDITIONAL_RETURN_NOT_ALLOWED      = "COMPILER-139";
    /**
     * Annotation {0} is not applicable.
     */
    public static final String ANNOTATION_NOT_APPLICABLE           = "COMPILER-140";
    /**
     * The argument name is not allowed.
     */
    public static final String ILLEGAL_ARG_NAME                    = "COMPILER-141";
    /**
     * Method name {0} collides with a property of the same name on class {1}.
     */
    public static final String METHOD_NAME_COLLISION               = "COMPILER-142";
    /**
     * Property name {0} collides with a method of the same name on class {1}.
     */
    public static final String PROPERTY_NAME_COLLISION             = "COMPILER-143";
    /**
     * Function {0} refers to a generic type {1}.
     */
    public static final String GENERIC_TYPE_NOT_ALLOWED            = "COMPILER-144";
    /**
     * Unresolvable type parameter(s): {0}.
     */
    public static final String TYPE_PARAMS_UNRESOLVABLE            = "COMPILER-145";
    /**
     * Property reference ('&') is invalid.
     */
    public static final String INVALID_PROPERTY_REF                = "COMPILER-146";
    /**
     * Validator cannot be abstract.
     */
    public static final String VALIDATOR_BODY_MISSING              = "COMPILER-147";
    /**
     * A duplicate name is used for a class or package: {0}
     */
    public static final String DUPLICATE_NAME                      = "COMPILER-148";
    /**
     * Virtual child {0} cannot be new'ed until after the parent has been constructed.
     */
    public static final String PARENT_NOT_CONSTRUCTED              = "COMPILER-149";
    /**
     * Parameter type mismatch for {0} method; required {1}, actual {2}.
     */
    public static final String INCOMPATIBLE_PARAMETER_TYPE         = "COMPILER-150";
    /**
     * Return type mismatch for {0} method; required {1}, actual {2}.
     */
    public static final String INCOMPATIBLE_RETURN_TYPE            = "COMPILER-151";
    /**
     * Return count mismatch for {0} method; required {1}, actual {2}.
     */
    public static final String INCOMPATIBLE_RETURN_COUNT           = "COMPILER-152";
    /**
     * Argument {0} must have a default value.
     */
    public static final String DEFAULT_VALUE_REQUIRED              = "COMPILER-153";
    /**
     * The Map constant contains duplicate keys: {0}.
     */
    public static final String MAP_KEYS_DUPLICATE                  = "COMPILER-154";
    /**
     * Non-terminating loop.
     */
    public static final String INFINITE_LOOP                       = "COMPILER-155";
    /**
     * Construct target {0} is not in the inheritance chain.
     */
    public static final String INVALID_CONSTRUCT_CALL              = "COMPILER-156";
    /**
     * Super class constructor is skipped.
     */
    public static final String SUPER_CONSTRUCTOR_SKIPPED           = "COMPILER-157";
    /**
     * An assertion used as an expression must throw (must not complete).
     */
    public static final String ASSERT_EXPRESSION_MUST_THROW        = "COMPILER-158";
    /**
     * An "outer" reference to {0} is required to access the {1} property.
     */
    public static final String NO_OUTER_PROPERTY                   = "COMPILER-159";
    /**
     * An "outer" reference to {0} is required to access the {1} method.
     */
    public static final String NO_OUTER_METHOD                     = "COMPILER-160";
    /**
     * Virtual constructor must be public and abstract.
     */
    public static final String ILLEGAL_VIRTUAL_CONSTRUCTOR         = "COMPILER-161";
    /**
     * Property {0} on the {1} type is not accessible.
     */
    public static final String PROPERTY_INACCESSIBLE               = "COMPILER-162";
    /**
     * Illegal literal value: {0}.
     */
    public static final String BAD_LITERAL                         = "COMPILER-163";
    /**
     * {0} is not yet implemented.
     */
    public static final String NOT_IMPLEMENTED                     = "COMPILER-NI";
    }
