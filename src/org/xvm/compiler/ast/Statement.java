package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import java.util.Set;
import java.util.TreeMap;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Argument;
import org.xvm.asm.Parameter;
import org.xvm.asm.Register;

import org.xvm.asm.Register.Assignment;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.compiler.Token.Id;
import org.xvm.util.Severity;


/**
 * Base class for all Ecstasy statements.
 */
public abstract class Statement
        extends AstNode
    {
    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean usesSuper()
        {
        for (AstNode node : children())
            {
            if (!(node instanceof ComponentStatement) && node.usesSuper())
                {
                return true;
                }
            }

        return false;
        }

    /**
     * @return the label corresponding to the beginning of the Statement
     */
    public Label getBeginLabel()
        {
        Label label = m_labelBegin;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelBegin = label = new Label();
            }
        return label;
        }

    /**
     * @return the label corresponding to the ending of the Statement
     */
    public Label getEndLabel()
        {
        Label label = m_labelEnd;
        if (label == null)
            {
            assert !m_fEmitted;
            m_labelEnd = label = new Label();
            }
        return label;
        }

    /**
     * Mark the statement as completing by short-circuiting.
     */
    public void shortCircuit() // TODO re-evaluate (currently not used - why not?)
        {
        m_fShortCircuited = true;
        }

    /**
     * @return true iff a "break" statement can apply to this statement
     */
    public boolean canBreak()
        {
        return false;
        }

    /**
     * @return the label to jump to when a "break" occurs within (or for) this statement
     */
    public Label getBreakLabel()
        {
        assert canBreak();
        return getEndLabel();
        }

    /**
     * @return true iff a "continue" statement can apply to this statement
     */
    public boolean canContinue()
        {
        return false;
        }

    /**
     * @return the label to jump to when a "continue" occurs within (or for) this statement
     */
    public Label getContinueLabel()
        {
        assert canContinue();
        throw notImplemented();
        }


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Before generating the code for the method body, resolve names and verify definite assignment,
     * etc.
     *
     * @param ctx    the compilation context for the statement
     * @param errs   the error listener to log to
     *
     * @return true iff the compilation can proceed
     */
    protected Statement validate(Context ctx, ErrorListener errs) // TODO make abstract
        {
        throw notImplemented();
        }

    /**
     * Generate the generic assembly code that wraps the contents of any statement.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean completes(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (fReachable)
            {
            updateLineNumber(code);
            }
        else
            {
            code = code.blackhole();
            }

        boolean fBeginLabel = m_labelBegin != null;
        if (fBeginLabel)
            {
            code.add(m_labelBegin);
            }

        boolean fCompletes = fReachable & emit(ctx, fReachable, code, errs);

        // a being label should not have been requested during the emit stage unless it had been
        // requested previously (since it's too late to add it now!)
        assert fBeginLabel == (m_labelBegin != null);

        if (m_labelEnd != null)
            {
            code.add(m_labelEnd);
            }

        m_fEmitted = true;
        return fCompletes || fReachable && m_fShortCircuited;
        }

    /**
     * Generate the statement-specific assembly code.
     *
     * @param ctx         the compilation context for the statement
     * @param fReachable  true iff the statement is reachable
     * @param code        the code object to which the assembly is added
     * @param errs        the error listener to log to
     *
     * @return true iff the statement completes
     */
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) // TODO make abstract
        {
        throw notImplemented();
        }


    // ----- inner class: compiler Context ---------------------------------------------------------

    /**
     * Compiler context for compiling a method body.
     */
    public abstract static class Context
        {
        /**
         * Construct a Context.
         *
         * @param ctxOuter  the context that this Context is nested within
         */
        protected Context(Context ctxOuter)
            {
            m_ctxOuter = ctxOuter;
            }

        /**
         * @return the outer context
         */
        protected Context getOuterContext()
            {
            return m_ctxOuter;
            }

        /**
         * @param ctxOuter  the outer context to use
         */
        protected void setOuterContext(Context ctxOuter)
            {
            m_ctxOuter = ctxOuter;
            }

        /**
         * @return the inner context (if any)
         */
        protected Context getInnerContext()
            {
            return m_ctxInner;
            }

        /**
         * @param ctxInner  the Inner context to use
         */
        protected void setInnerContext(Context ctxInner)
            {
            m_ctxInner = ctxInner;
            }

        /**
         * @return the MethodStructure that the context represents (and specifically, not a
         *         MethodStructure representing an implicit or explicit lambda, since those are
         *         treated "transparently" because of captures)
         */
        public MethodStructure getMethod()
            {
            return getOuterContext().getMethod();
            }

        /**
         * @return true iff the containing MethodStructure is a method (not a function), which means
         *         that "this", "super", and other reserved registers are available
         */
        public boolean isMethod()
            {
            return getOuterContext().isMethod();
            }

        /**
         * @return true iff the containing MethodStructure is a function (neither a method nor a
         *         constructor), which means that "this", "super", and other reserved registers are
         *         not available
         */
        public boolean isFunction()
            {
            return getOuterContext().isFunction();
            }

        /**
         * @return true iff the containing MethodStructure is a constructor, which means that
         *         "this" and "this:struct" are available, but other reserved registers that require
         *         an instance of the class are not available
         */
        public boolean isConstructor()
            {
            return getOuterContext().isConstructor();
            }

        /**
         * @return the source for the method
         */
        public Source getSource()
            {
            return getOuterContext().getSource();
            }

        /**
         * @return the containing ClassStructure for the method
         */
        public ClassStructure getThisClass()
            {
            return getOuterContext().getThisClass();
            }

        /**
         * @return the formal type of the containing class
         */
        public TypeConstant getThisType()
            {
            return getThisClass().getFormalType();
            }

        /**
         * @return the ConstantPool
         */
        public ConstantPool pool()
            {
            return getOuterContext().pool();
            }

        /**
         * Verify that this is a forkable context.
         */
        protected void checkForkable()
            {
            Context ctxInner = getInnerContext();
            if (ctxInner != null && ctxInner != this)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * Create a nested fork of this context.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @return the new (forked) context
         */
        public Context fork()
            {
            checkForkable();

            setInnerContext(this);
            return new NestedContext(this);
            }

        /**
         * Verify that this is a forked context.
         */
        protected void checkForked()
            {
            if (getInnerContext() != this)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * Join multiple forks of this context back together.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param contexts  the previously forked contexts
         */
        public void join(Context... contexts)
            {
            checkForked();

            for (Context ctx : contexts)
                {
                if (ctx.getOuterContext() != this)
                    {
                    throw new IllegalStateException("not a fork of this context");
                    }
                }

            // TODO merge info

            setInnerContext(null);
            }

        /**
         * Verify that this is the innermost context.
         */
        protected void checkInnermost()
            {
            if (getInnerContext() != null)
                {
                throw new IllegalStateException();
                }
            }

        /**
         * Used in the validation phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public Context enterScope()
            {
            checkInnermost();

            Context ctxInner = new NestedContext(this);
            setInnerContext(ctxInner);
            return ctxInner;
            }

        /**
         * Exit the scope that was created by calling {@link #enterScope()}. Used in the validation
         * phase to track scopes.
         * <p/>
         * Note: This can only be used during the validate() stage.
         */
        public Context exitScope()
            {
            // note: changes to this method should be carefully evaluated for inclusion in the
            // CaptureContext.exitScope() sub-class method as well

            checkInnermost();

            Context ctxOuter = getOuterContext();
            assert ctxOuter.getInnerContext() == this;

            // copy variable assignment information from this scope to outer scope
            Map<String, Assignment> mapInner = getDefiniteAssignments();
            if (!mapInner.isEmpty())
                {
                Map<String, Assignment> mapOuter = ctxOuter.ensureDefiniteAssignments();
                for (Entry<String, Assignment> entry : mapInner.entrySet())
                    {
                    String     sName = entry.getKey();
                    Assignment asn   = entry.getValue();
                    if (isVarDeclaredInThisScope(sName))
                        {
                        // we have unwound all the way back to the declaration context for the
                        // variable at this point, so if it is proven to be effectively final, that
                        // information is stored off, for example so that captures can make use of
                        // that knowledge (i.e. capturing a value of type T, instead of a Ref<T>)
                        if (asn.isEffectivelyFinal())
                            {
                            ((Register) getVar(sName)).markEffectivelyFinal();
                            }
                        }
                    else
                        {
                        mapOuter.put(sName, asn);
                        }
                    }
                }

            Set<String> setRsvd = getReservedNameSet();
            if (!setRsvd.isEmpty())
                {
                ctxOuter.ensureReservedNameSet().addAll(setRsvd);
                }

            setOuterContext(null);
            ctxOuter.setInnerContext(null);
            return ctxOuter;
            }

        /**
         * Register the specified variable name in this context.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param tokName  the token from the source code for the variable
         * @param reg      the register representing the variable
         * @param errs     the error list to log to
         */
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            checkInnermost();

            String sName = tokName.getValueText();
            if (isVarDeclaredInThisScope(sName)) // TODO allow explicit shadowing?
                {
                tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_DEFINED, sName);
                }

            ensureNameMap().put(sName, reg);
            }

        /**
         * See if the specified name declares an argument within this context.
         *
         * @param sName  the variable name
         *
         * @return a Register iff the name is registered to a register; otherwise null
         */
        public final Argument getVar(String sName)
            {
            return getVar(sName, null, null);
            }

        /**
         * See if the specified name declares an argument within this context.
         *
         * @param name  the name token
         * @param errs  the error list to log errors to
         *
         * @return a Register iff the name is registered to a register; otherwise null
         */
        public final Argument getVar(Token name, ErrorListener errs)
            {
            return getVar(name.getValueText(), name, errs);
            }

        /**
         * Internal implementation of getVar() that allows the lookup to be done with or without a
         * token.
         *
         * @param sName  the name to look up
         * @param name   the token to use for error reporting (optional)
         * @param errs   the error list to use for error reporting (optional)
         *
         * @return the argument for the variable, or null
         */
        protected Argument getVar(String sName, Token name, ErrorListener errs)
            {
            Argument arg = getNameMap().get(sName);
            if (arg instanceof Register)
                {
                return arg;
                }

            Context ctxOuter = getOuterContext();
            return ctxOuter == null
                    ? null
                    : ctxOuter.getVar(name, errs);
            }

        /**
         * Determine if the specified variable name refers to a local variable (including
         * parameters, but not including reserved names, for example).
         *
         * @param sName  the variable name
         *
         * @return true iff a variable of that name exists in this scope
         */
        public boolean isLocalVar(String sName)
            {
            Context ctxOuter = getOuterContext();
            return getNameMap().containsKey(sName)
                    || (ctxOuter != null && ctxOuter.isLocalVar(sName));
            }

        /**
         * Determine if the specified variable name is already declared in the current scope.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the variable name
         *
         * @return true iff a variable of that name is already declared in this scope
         */
        public boolean isVarDeclaredInThisScope(String sName)
            {
            return getNameMap().containsKey(sName);
            }

        /**
         * Obtain the assignment information for a variable name.
         *
         * @param sName  the variable name
         *
         * @return the Assignment (VAS data) for the variable name
         */
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = getDefiniteAssignments().get(sName);
            if (asn != null)
                {
                return asn;
                }

            // if the variable was declared in this scope, then we should have a variable assignment
            // status in this scope
            assert !getNameMap().containsKey(sName);

            Context ctxOuter = getOuterContext();
            return ctxOuter == null
                    ? null
                    : ctxOuter.getVarAssignment(sName);
            }

        /**
         * Associate assignment information with a variable name.
         *
         * @param sName  the variable name
         * @param asn    the Assignment (VAS data) to associate with the variable name
         */
        public void setVarAssignment(String sName, Assignment asn)
            {
            ensureDefiniteAssignments().put(sName, asn);
            }

        /**
         * Determine if the name refers to a readable variable. A variable is only readable if it
         * has been definitely assigned a value.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the variable name
         *
         * @return true iff the name refers to a variable, and the variable can be read from
         */
        public boolean isVarReadable(String sName)
            {
            if (resolveReservedName(sName) != null)
                {
                return isReservedNameReadable(sName);
                }

            Assignment asn = getVarAssignment(sName);
            assert asn != null;
            return asn.isDefinitelyAssigned();
            }

        /**
         * Mark the specified variable as being read from within this context.
         *
         * @param sName     the variable name
         */
        public final void markVarRead(String sName)
            {
            if (resolveReservedName(sName) == null)
                {
                markVarRead(sName, null, null);
                }
            else
                {
                markReservedNameRead(sName, null, null);
                }
            }

        /**
         * Mark the specified variable as being read from within this context.
         *
         * @param tokName     the variable name as a token from the source code
         * @param errs        the error list to log to
         */
        public final void markVarRead(Token tokName, ErrorListener errs)
            {
            String sName = tokName.getValueText();
            if (resolveReservedName(sName) == null)
                {
                markVarRead(sName, tokName, errs);
                }
            else
                {
                markReservedNameRead(sName, tokName, errs);
                }
            }

        /**
         * Mark the specified variable as being read from within this context.
         *
         * @param sName    the variable name
         * @param tokName  the variable name as a token from the source code (optional)
         * @param errs     the error list to log to (optional)
         */
        protected void markVarRead(String sName, Token tokName, ErrorListener errs)
            {
            if (resolveReservedName(sName) != null)
                {
                markReservedNameRead(sName, tokName, errs);
                return;
                }

            // this method isn't supposed to be called for variable names that don't exist
            assert getVar(sName) != null;

            // a read of an unassigned value shouldn't occur
            if (!isVarReadable(sName))
                {
                if (tokName != null && errs != null)
                    {
                    tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_UNASSIGNED, sName);

                    // record that the variable is definitely assigned so that the error will
                    // not be repeated unnecessarily within this context
                    setVarAssignment(sName, getVarAssignment(sName).applyAssignment());
                    }
                else
                    {
                    throw new IllegalStateException("illegal var read: name=" + sName);
                    }
                }
            }

        /**
         * Determine if the name refers to a readable reserved name.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the reserved name
         *
         * @return true iff the name refers to a reserved name, and the reserved name can be read
         */
        public boolean isReservedNameReadable(String sName)
            {
            return getOuterContext().isReservedNameReadable(sName);
            }

        /**
         * Mark the specified reserved name as being read from within this context.
         *
         * @param sName  the reserved name
         */
        public final void markReservedNameRead(String sName)
            {
            markReservedNameRead(sName, null, null);
            }

        /**
         * Mark the specified reserved name as being read from within this context.
         *
         * @param tokName  the reserved name as a token from the source code
         * @param errs     the error list to log to
         */
        public final void markReservedNameRead(Token tokName, ErrorListener errs)
            {
            markReservedNameRead(tokName.getValueText(), tokName, errs);
            }

        /**
         * Mark the specified reserved name as being read from within this context.
         *
         * @param sName    the reserved name
         * @param tokName  the reserved name as a token from the source code (optional)
         * @param errs     the error list to log to (optional)
         */
        protected void markReservedNameRead(String sName, Token tokName, ErrorListener errs)
            {
            // store the name in the set of accessed names regardless, to avoid reporting the same
            // error more than once
            Set<String> setRsvd = ensureReservedNameSet();
            if (!setRsvd.contains(sName))
                {
                if (isReservedNameReadable(sName))
                    {
                    setRsvd.add(sName);
                    }
                else if (tokName != null && errs != null)
                    {
                    tokName.log(errs, getSource(), Severity.ERROR,
                            sName.startsWith("this") ? Compiler.NO_THIS     :
                            sName.equals("super")    ? Compiler.NO_SUPER    :
                                                       Compiler.NAME_MISSING, sName);

                    // add the variable to the reserved names that are allowed, to avoid repeating
                    // the same error logging
                    setRsvd.add(sName);
                    }
                }
            }

        /**
         * Determine if the name refers to a writable variable.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the variable name
         *
         * @return true iff the name refers to a variable, and the variable can be written to
         */
        public boolean isVarWritable(String sName)
            {
            if (isVarDeclaredInThisScope(sName))
                {
                // TODO a "val" cannot be written to once it is definitely assigned
                // TODO add compiler option to disallow writes to parameters
                return true;
                }

            Context ctxOuter = getOuterContext();
            return ctxOuter != null && ctxOuter.isVarWritable(sName);
            }

        /**
         * Mark the specified variable as being written to within this context.
         *
         * @param sName  the variable name
         */
        public final void markVarWrite(String sName)
            {
            markVarWrite(sName, null, null);
            }

        /**
         * Mark the specified variable as being written to within this context.
         *
         * @param tokName  the variable name as a token from the source code
         * @param errs     the error list to log to (optional)
         */
        public final void markVarWrite(Token tokName, ErrorList errs)
            {
            markVarWrite(tokName.getValueText(), tokName, errs);
            }

        /**
         * Mark the specified variable as being written to within this context.
         *
         * @param sName       the variable name
         * @param tokName     the variable name as a token from the source code (optional)
         * @param errs        the error list to log to (optional)
         */
        protected void markVarWrite(String sName, Token tokName, ErrorListener errs)
            {
            // this method isn't supposed to be called for variable names that don't exist
            assert getVar(sName) != null;

            if (isVarWritable(sName))
                {
                setVarAssignment(sName, getVarAssignment(sName).applyAssignment());
                }
            else if (tokName != null && errs != null)
                {
                tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_ASSIGNMENT_ILLEGAL, sName);
                }
            else
                {
                throw new IllegalStateException("illegal var write: name=" + sName);
                }
            }

        /**
         * @return a read-only map containing definitely assigned variable names; never null
         */
        protected Map<String, Assignment> getDefiniteAssignments()
            {
            Map<String, Assignment> map = m_mapAssigned;
            return map == null
                    ? Collections.EMPTY_MAP
                    : map;
            }

        /**
         * Map from variable name to Boolean value or null, with null indicating "not assigned
         * within this context", false indicating "assgined once within this context", and true
         * indicating "assigned multiple times within this context".
         *
         * @return a readable and writable set of definitely assigned variable names; never null
         */
        protected Map<String, Assignment> ensureDefiniteAssignments()
            {
            Map<String, Assignment> map = m_mapAssigned;
            if (map == null)
                {
                m_mapAssigned = map = new HashMap<>();
                }
            return map;
            }

        /**
         * @return a read-only map containing reserved names that are used; never null
         */
        protected Set<String> getReservedNameSet()
            {
            Set<String> set = m_setReservedNames;
            return set == null
                    ? Collections.EMPTY_SET
                    : set;
            }

        /**
         * Set of reserved names that are used.
         *
         * @return a readable and writable set of used reserved names; never null
         */
        protected Set<String> ensureReservedNameSet()
            {
            Set<String> set = m_setReservedNames;
            if (set == null)
                {
                m_setReservedNames = set = new HashSet<>();
                }
            return set;
            }

        /**
         * Resolve the name of a variable, structure, etc.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the name to resolve
         *
         * @return the Argument representing the meaning of the name, or null
         */
        public final Argument resolveName(String sName)
            {
            return resolveName(sName, null, null);
            }

        /**
         * Resolve the name of a variable, structure, etc.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param name  the name token to resolve
         *
         * @return the Argument representing the meaning of the name, or null
         */
        public final Argument resolveName(Token name, ErrorListener errs)
            {
            return resolveName(name.getValueText(), name, errs);
            }

        /**
         * Resolve the name of a variable, structure, etc.
         * <p/>
         * Note: This can only be used during the validate() stage.
         *
         * @param sName  the name to resolve
         * @param name   the token from the source for the name to resolve (optional)
         * @param errs   the error list to log to (optional)
         *
         * @return the Argument representing the meaning of the name, or null
         */
        protected Argument resolveName(String sName, Token name, ErrorListener errs)
            {
            Argument arg = resolveReservedName(sName, name, errs);
            if (arg == null)
                {
                arg = resolveRegularName(sName, name, errs);
                }
            return arg;
            }

        /**
         * @return the read-only map that provides a name-to-argument lookup
         */
        protected Map<String, Argument> getNameMap()
            {
            return m_mapByName == null
                    ? Collections.EMPTY_MAP
                    : m_mapByName;
            }

        /**
         * @return the map that provides a name-to-argument lookup
         */
        protected Map<String, Argument> ensureNameMap()
            {
            Map<String, Argument> mapByName = m_mapByName;

            if (mapByName == null)
                {
                mapByName = new HashMap<>();
                initNameMap(mapByName);
                m_mapByName = mapByName;
                }

            return mapByName;
            }

        /**
         * Initialize the map that holds named arguments.
         *
         * @param mapByName  the map from simple name to argument
         */
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            }

        /**
         * Resolve a name (other than a reserved name) to an argument.
         *
         * @param name  the name token
         * @param errs  the error list to log errors to
         *
         * @return an Argument iff the name is registered to an argument; otherwise null
         */
        public final Argument resolveRegularName(Token name, ErrorListener errs)
            {
            return resolveRegularName(name.getValueText(), name, errs);
            }

        /**
         * Resolve a name (other than a reserved name) to an argument.
         *
         * @param sName  the name to resolve
         *
         * @return an Argument iff the name is registered to an argument; otherwise null
         */
        public final Argument resolveRegularName(String sName)
            {
            return resolveRegularName(sName, null, null);
            }

        /**
         * Resolve a name (other than a reserved name) to an argument.
         *
         * @param sName  the name to resolve
         * @param name   the name token for error reporting (optional)
         * @param errs   the error list to log errors to (optional)
         *
         * @return an Argument iff the name is registered to an argument; otherwise null
         */
        protected Argument resolveRegularName(String sName, Token name, ErrorListener errs)
            {
            Argument arg = getNameMap().get(sName);
            if (arg != null)
                {
                return arg;
                }

            return getOuterContext().resolveRegularName(name, errs);
            }

        /**
         * Resolve a reserved name to an argument.
         *
         * @param sName  the name to resolve
         *
         * @return an Argument iff the name resolves to a reserved name; otherwise null
         */
        public final Argument resolveReservedName(String sName)
            {
            return resolveReservedName(sName, null, ErrorListener.BLACKHOLE);
            }

        /**
         * Resolve a reserved name to an argument.
         *
         * @param name  the potentially reserved name token
         * @param errs  the error list to log errors to
         *
         * @return an Argument iff the name resolves to a reserved name; otherwise null
         */
        public final Argument resolveReservedName(Token name, ErrorListener errs)
            {
            return resolveReservedName(name.getValueText(), name, errs);
            }

        /**
         * Internal implementation of resolveReservedName that allows the resolution to be done with
         * or without a token.
         *
         * @param sName  the name to look up
         * @param name   the token to use for error reporting (optional)
         * @param errs   the error list to use for error reporting (optional)
         *
         * @return the argument for the reserved name, or null if no such reserved name can be
         *         resolved within this context
         */
        protected Argument resolveReservedName(String sName, Token name, ErrorListener errs)
            {
            return getOuterContext().resolveReservedName(sName, name, errs);
            }

        /**
         * Create a delegating context that allows an expression to resolve names based on the
         * specified type's contributions.
         *
         * As a result, it allows us to write:
         * <pre><code>
         *    Color color = Red;
         * </code></pre>
         *  instead of
         * <pre><code>
         *    Color color = Color.Red;
         * </code></pre>
         * or
         * <pre><code>
         *    if (color == Red)
         * </code></pre>
         *  instead of
         * <pre><code>
         *    if (color == Color.Red)
         * </code></pre>
         *
         * @param typeLeft  the "infer from" type
         *
         * @return a new context
         */
        public InferringContext createInferringContext(TypeConstant typeLeft)
            {
            return new InferringContext(this, typeLeft);
            }

        /**
         * Create a context that bridges from the current context into a special compilation mode in
         * which the values (or references / variables) of the outer context can be <i>captured</i>.
         *
         * @param body         the StatementBlock of the lambda, anonymous inner class, or statement
         *                     expression
         * @param atypeParams  types of the explicit parameters for the context (e.g. for a lambda)
         * @param asParams     names of the explicit parameters for the context (e.g. for a lambda)
         *
         * @return a capturing context
         */
        public CaptureContext createCaptureContext(StatementBlock body, TypeConstant[] atypeParams, String[] asParams)
            {
            return new CaptureContext(this, body, atypeParams, asParams);
            }

        /**
         * The outer context. The root context will not have an outer context, and an artificial
         * context may not have an outer context.
         */
        private Context m_ctxOuter;

        /**
         * The inner context, if this is not the inner-most context. (Set to "this" when forked.)
         */
        private Context m_ctxInner;

        /**
         * Each variable declared within this hcontext is registered in this map, along with the
         * Argument that represents it.
         */
        private Map<String, Argument> m_mapByName;

        /**
         * Each variable assigned within this context is registered in this map. The boolean value
         * represents multiple assignments.
         */
        private Map<String, Assignment> m_mapAssigned;
        
        /**
         * The set of accessed reserved names within the context. 
         */
        private Set<String> m_setReservedNames;
        }


    /**
     * The outermost compiler context for compiling a method body. This context maintains a link
     * with the method body that is being compiled, and represents the parameters to the method and
     * the global names visible to the method.
     */
    public static class RootContext
            extends Context
        {
        public RootContext(MethodStructure method, StatementBlock stmtBody)
            {
            super(null);

            m_method   = method;
            m_stmtBody = stmtBody;
            }

        @Override
        public MethodStructure getMethod()
            {
            return m_method;
            }

        @Override
        public Source getSource()
            {
            return m_stmtBody.getSource();
            }

        @Override
        public ClassStructure getThisClass()
            {
            Component parent = m_method;
            while (!(parent instanceof ClassStructure))
                {
                parent = parent.getParent();
                }
            return (ClassStructure) parent;
            }

        @Override
        public ConstantPool pool()
            {
            return m_method.getConstantPool();
            }

        @Override
        public Context fork()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public void join(Context... contexts)
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public Context enterScope()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            checkValidating();
            throw new IllegalStateException();
            }

        @Override
        public boolean isVarDeclaredInThisScope(String sName)
            {
            Argument arg = getNameMap().get(sName);
            if (arg instanceof Register)
                {
                Register reg = (Register) arg;
                return reg.getIndex() >= 0 || reg.isUnknown();
                }

            return false;
            }

        @Override
        public boolean isVarReadable(String sName)
            {
            if (isVarDeclaredInThisScope(sName))
                {
                return super.isVarReadable(sName);
                }

            Argument arg = ensureNameMap().get(sName);
            if (arg instanceof Register)
                {
                return ((Register) arg).isReadable();
                }

            // module constant (this:module) or property constant (local property access)
            return arg instanceof IdentityConstant;
            }

        @Override
        public boolean isReservedNameReadable(String sName)
            {
            checkValidating();

            switch (sName)
                {
                case "this":
                case "this:struct":
                    return !isFunction();

                case "this:target":
                case "this:public":
                case "this:protected":
                case "this:private":
                    return !isFunction() && !isConstructor();

                case "this:module":
                case "this:service":
                    return true;

                case "super":
                    {
                    TypeConstant   typePro    = pool().ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                    TypeInfo       info       = typePro.ensureTypeInfo();
                    MethodConstant idMethod   = getMethod().getIdentityConstant();
                    MethodInfo     infoMethod = info.getMethodById(idMethod);
                    return infoMethod.hasSuper(info);
                    }

                default:
                    throw new IllegalArgumentException("no such reserved name: " + sName);
                }
            }

        @Override
        public boolean isVarWritable(String sName)
            {
            Argument arg = ensureNameMap().get(sName);
            if (arg instanceof Register)
                {
                return ((Register) arg).isWritable();
                }
            else if (arg instanceof PropertyConstant)
                {
                // the context only answers the question of what the _first_ name is, so
                // in the case of "a.b.c", this method is called only for "a", so if someone
                // is asking if it's settable, then obviously there is no ".b.c" following;
                // it's obviously a local property (since there is no "target." before it),
                // so the only check to do here is to make sure that it is settable, i.e. not
                // a calculated property
                // TODO: check for a calculated property
                return true;
                }

            return false;
            }

        @Override
        protected Argument resolveRegularName(String sName, Token name, ErrorListener errs)
            {
            checkValidating();

            // check if the name is a parameter name, or a global name that has already been looked
            // up and cached
            Map<String, Argument> mapByName = ensureNameMap();
            Argument              arg       = mapByName.get(sName);
            if (arg == null)
                {
                // resolve the name from outside of this statement
                arg = new NameResolver(m_stmtBody, sName)
                        .forceResolve(errs == null ? ErrorListener.BLACKHOLE : errs);
                if (arg != null)
                    {
                    mapByName.put(sName, arg);
                    }
                }

            return arg;
            }

        @Override
        protected Argument getVar(String sName, Token name, ErrorListener errs)
            {
            Argument arg = super.getVar(sName, name, errs);
            return arg == null
                    ? resolveReservedName(sName, name, errs)
                    : arg;
            }

        @Override
        protected Argument resolveReservedName(String sName, Token name, ErrorListener errs)
            {
            checkValidating();

            Map<String, Argument> mapByName = ensureNameMap();
            Argument              arg       = mapByName.get(sName);
            if (arg instanceof Register && ((Register) arg).isPredefined())
                {
                return arg;
                }

            ConstantPool pool = pool();
            TypeConstant type;
            int          nReg;
            switch (sName)
                {
                case "this":
                    if (isConstructor())
                        {
                        type = pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT);
                        nReg = Op.A_STRUCT;
                        break;
                        }
                    // fall through
                case "this:target":
                    type = getThisType();
                    nReg = Op.A_TARGET;
                    break;

                case "this:public":
                    type = getThisType();
                    assert type.getAccess() == Access.PUBLIC;
                    nReg = Op.A_PUBLIC;
                    break;

                case "this:protected":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.PROTECTED);
                    nReg = Op.A_PROTECTED;
                    break;

                case "this:private":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.PRIVATE);
                    nReg = Op.A_PRIVATE;
                    break;

                case "this:struct":
                    type = pool.ensureAccessTypeConstant(getThisType(), Access.STRUCT);
                    nReg = Op.A_STRUCT;
                    break;

                case "this:service":
                    type = pool.typeService();
                    nReg = Op.A_SERVICE;
                    break;

                case "super":
                    {
                    type = getMethod().getIdentityConstant().getSignature().asFunctionType();
                    nReg = Op.A_SUPER;
                    break;
                    }

                case "this:module":
                    // the module can be resolved to the actual module component at compile time
                    return getModule().getIdentityConstant();

                default:
                    return null;
                }

            arg = new Register(type, nReg);
            mapByName.put(sName, arg);
            return arg;
            }

        @Override
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            MethodStructure         method      = m_method;
            Map<String, Assignment> mapAssigned = ensureDefiniteAssignments();
            for (int i = 0, c = method.getParamCount(); i < c; ++i)
                {
                Parameter param = method.getParam(i);
                String    sName = param.getName();
                if (!sName.equals(Id.IGNORED.TEXT))
                    {
                    mapByName.put(sName, new Register(param.getType(), i));

                    // the variable has been definitely assigned, but not multiple times (i.e. it's
                    // still effectively final)
                    mapAssigned.put(sName, Assignment.AssignedOnce);
                    }
                }
            }

        @Override
        public boolean isMethod()
            {
            return !isFunction() && !isConstructor();
            }

        @Override
        public boolean isFunction()
            {
            if (isConstructor())
                {
                return false;
                }

            Component parent = m_method;
            while (true)
                {
                switch (parent.getFormat())
                    {
                    case INTERFACE:
                    case CLASS:
                    case CONST:
                    case ENUM:
                    case ENUMVALUE:
                    case MIXIN:
                    case SERVICE:
                    case PACKAGE:
                    case MODULE:
                        return false;

                    case METHOD:
                        if (parent.isStatic())
                            {
                            return true;
                            }
                        break;

                    case PROPERTY:
                    case MULTIMETHOD:
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                parent = parent.getParent();
                }
            }

        @Override
        public boolean isConstructor()
            {
            return m_method.isConstructor();
            }

        ModuleStructure getModule()
            {
            Component parent = m_method;
            while (!(parent instanceof ModuleStructure))
                {
                parent = parent.getParent();
                }
            return (ModuleStructure) parent;
            }

        @Override
        public Context exitScope()
            {
            checkValidating();
            throw new IllegalStateException();
            }

        /**
         * @return a Context that can be used while validating code
         */
        public Context validatingContext()
            {
            checkValidating();
            return super.enterScope();
            }

        /**
         * @return a Context that can be used while emitting code
         */
        public Context emittingContext()
            {
            checkValidating();
            getInnerContext().exitScope();
            m_fEmitting = true;
            return this;
            }

        private void checkValidating()
            {
            if (m_fEmitting)
                {
                throw new IllegalStateException();
                }
            }

        private void checkEmitting()
            {
            if (!m_fEmitting)
                {
                throw new IllegalStateException();
                }
            }

        private MethodStructure m_method;
        private StatementBlock  m_stmtBody;
        private boolean         m_fEmitting;
        private boolean         m_fLoggedNoThis;
        }


    /**
     * A nested context, representing a separate scope and/or code path.
     */
    public static class NestedContext
            extends Context
        {
        public NestedContext(Context ctxOuter)
            {
            super(ctxOuter);
            }
        }


    /**
     * A delegating context that allows an expression to resolve names based on the specified type's
     * contributions.
     *
     * As a result, it allows us to write:
     * <pre><code>
     *    Color color = Red;
     * </code></pre>
     *  instead of
     * <pre><code>
     *    Color color = Color.Red;
     * </code></pre>
     * or
     * <pre><code>
     *    if (color == Red)
     * </code></pre>
     *  instead of
     * <pre><code>
     *    if (color == Color.Red)
     * </code></pre>
     */
    public static class InferringContext
            extends NestedContext
        {
        /**
         * Construct an InferringContext.
         *
         * @param ctxOuter  the context within which this context is nested
         * @param typeLeft  the type from which this context can draw additional names for purposes
         *                  of resolution
         */
        public InferringContext(Context ctxOuter, TypeConstant typeLeft)
            {
            super(ctxOuter);

            m_typeLeft = typeLeft;
            }

        @Override
        protected Argument resolveRegularName(String sName, Token name, ErrorListener errs)
            {
            Argument arg = super.resolveRegularName(sName, name, errs);
            if (arg != null)
                {
                return arg;
                }

            Component.SimpleCollector collector = new Component.SimpleCollector();
            return m_typeLeft.resolveContributedName(sName, collector) ==
                    Component.ResolutionResult.RESOLVED
                    ? collector.getResolvedConstant()
                    : null;
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            getOuterContext().registerVar(tokName, reg, errs);
            }

        TypeConstant m_typeLeft;
        }


    /**
     * A context for compiling lamba expressions, anonymous inner classes, and any other construct
     * that "captures" variables from an outer context.
     * <p/>TODO capture "this" (makes a lambda into a method, or a static anonymous class into an instance anonymous class)
     */
    public static class CaptureContext
            extends Context
        {
        /**
         * Construct a CaptureContext.
         *
         * @param ctxOuter     the context within which this context is nested
         * @param body         the StatementBlock of the lambda / inner class, whose parent is one
         *                     of: NewExpression, LambdaExpression, or StatementExpression
         * @param atypeParams  types of the explicit parameters for the context (e.g. for a lambda)
         * @param asParams     names of the explicit parameters for the context (e.g. for a lambda)
         */
        public CaptureContext(Context ctxOuter, StatementBlock body, TypeConstant[] atypeParams, String[] asParams)
            {
            super(ctxOuter);

            assert atypeParams == null && asParams == null
                || atypeParams != null && asParams != null && atypeParams.length == asParams.length;
            m_atypeParams = atypeParams;
            m_asParams    = asParams;
            }

        @Override
        public Context exitScope()
            {
            checkInnermost();
    
            Context ctxOuter = getOuterContext();
            assert ctxOuter.getInnerContext() == this;

            // apply variable assignment information from the capture scope to the variables
            // captured from the outer scope
            for (Entry<String, Boolean> entry : getCaptureMap().entrySet())
                {
                if (entry.getValue())
                    {
                    String     sName  = entry.getKey();
                    Assignment asnOld = ctxOuter.getVarAssignment(sName);
                    Assignment asnNew = asnOld.applyAssignmentFromCapture();
                    ctxOuter.setVarAssignment(sName, asnNew);
                    }
                }

            Set<String> setRsvd = getReservedNameSet();
            if (!setRsvd.isEmpty())
                {
                ctxOuter.ensureReservedNameSet().addAll(setRsvd);
                }

            setOuterContext(null);
            ctxOuter.setInnerContext(null);
            return ctxOuter;
            }

        @Override
        protected void markVarRead(String sName, Token tokName, ErrorListener errs)
            {
            if (isVarReadable(sName))
                {
                Argument argRsvd = resolveReservedName(sName);
                if (argRsvd == null)
                    {
                    Map<String, Boolean> map = ensureCaptureMap();
                    if (!m_mapCapture.containsKey(sName))
                        {
                        m_mapCapture.put(sName, false);
                        }
                    }
                else if (argRsvd instanceof Register)
                    {
                    // TODO "this" means that the lambda must be a method
                    switch (((Register) argRsvd).getIndex())
                        {
                        case Op.A_TARGET:
                        case Op.A_PUBLIC:
                        case Op.A_PROTECTED:
                        case Op.A_PRIVATE:
                            // the lambda has to be a method
                            // TODO
                            break;

                        case Op.A_STRUCT:
                            // this is a special problem TODO
                            throw new UnsupportedOperationException();

                        case Op.A_SUPER:
                        }
                    }
                }

            super.markVarRead(sName, tokName, errs);
            }

        @Override
        protected void markVarWrite(String sName, Token tokName,
                ErrorListener errs)
            {
            // TODO make sure that sName is in our list of "must be captured as Var<T>"
            // TODO make sure that the variable is marked in the ***containing*** context as being written

            super.markVarWrite(sName, tokName, errs);
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        public Map<String, Boolean> getCaptureMap()
            {
            return m_mapCapture == null
                    ? Collections.EMPTY_MAP
                    : m_mapCapture;
            }

        @Override
        protected void initNameMap(Map<String, Argument> mapByName)
            {
            TypeConstant[] atypeParams = m_atypeParams;
            String[]       asParams    = m_asParams;
            int            cParams     = atypeParams == null ? 0 : atypeParams.length;
            for (int i = 0; i < cParams; ++i)
                {
                TypeConstant type  = atypeParams[i];
                String       sName = asParams[i];
                if (!sName.equals(Id.IGNORED.TEXT))
                    {
                    mapByName.put(sName, new Register(type));

                    // the variable has been definitely assigned, but not multiple times (i.e. it's
                    // still effectively final)
                    ensureDefiniteAssignments().put(sName, Assignment.AssignedOnce);
                    }
                }
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        private Map<String, Boolean> ensureCaptureMap()
            {
            Map<String, Boolean> map = m_mapCapture;
            if (map == null)
                {
                // use a tree map, as it will keep the captures in alphabetical order, which will
                // help to produce the lambdas with a "predictable" signature
                m_mapCapture = map = new TreeMap<>();
                }

            return map;
            }

        private TypeConstant[] m_atypeParams;
        private String[] m_asParams;

        private Map<String, Boolean> m_mapCapture;
        }


    // ----- fields --------------------------------------------------------------------------------

    private Label   m_labelBegin;
    private Label   m_labelEnd;
    private boolean m_fShortCircuited;
    private boolean m_fEmitted;
    }
