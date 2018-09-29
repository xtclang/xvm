package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Register.Assignment;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * Compiler context for compiling a method body.
 */
public abstract class Context
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
     * Used in the validation phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context enter()
        {
        Context ctxInner = new NestedContext(this);
        setInnerContext(ctxInner);
        return ctxInner;
        }

    /**
     * Create a nested fork of this context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @param fWhenTrue  false iff the new context is for the "when false" fork (and thus true
     *                   iff the new context is for the "when true" fork)
     *
     * @return the new (forked) context
     */
    public Context fork(boolean fWhenTrue)
        {
        Context ctxInner = new ForkedContext(this, fWhenTrue);
        setInnerContext(ctxInner);
        return ctxInner;
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
    public InferringContext enterInferring(TypeConstant typeLeft)
        {
        InferringContext ctxInner = new InferringContext(this, typeLeft);
        setInnerContext(ctxInner);
        return ctxInner;
        }

    /**
     * Exit the scope that was created by calling {@link #enter()}. Used in the validation
     * phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context exit()
        {
        // TODO REVIEW
        // note: changes to this method should be carefully evaluated for inclusion in any
        //       sub-class that overrides this method

        Context ctxOuter = getOuterContext();
        assert ctxOuter.getInnerContext() == this;

        // copy variable assignment information from this scope to outer scope
        for (Entry<String, Assignment> entry : getDefiniteAssignments().entrySet())
            {
            String     sName    = entry.getKey();
            Assignment asnInner = entry.getValue();
            if (isVarDeclaredInThisScope(sName))
                {
                // we have unwound all the way back to the declaration context for the
                // variable at this point, so if it is proven to be effectively final, that
                // information is stored off, for example so that captures can make use of
                // that knowledge (i.e. capturing a value of type T, instead of a Ref<T>)
                if (asnInner.isEffectivelyFinal())
                    {
                    ((Register) getVar(sName)).markEffectivelyFinal();
                    }
                }
            else
                {
                promoteAssignment(sName, asnInner);
                }
            }

        setOuterContext(null);
        ctxOuter.setInnerContext(null);
        return ctxOuter;
        }

    /**
     * Promote assignment information from this context to its enclosing context.
     *
     * @param sName     the variable name
     * @param asnInner  the variable assignment information to promote to the enclosing context
     */
    protected void promoteAssignment(String sName, Assignment asnInner)
        {
        getOuterContext().setVarAssignment(sName, asnInner);
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
        String sName = tokName.getValueText();
        if (isVarDeclaredInThisScope(sName))
            {
            tokName.log(errs, getSource(), Severity.ERROR, org.xvm.compiler.Compiler.VAR_DEFINED, sName);
            }

        ensureNameMap().put(sName, reg);
        ensureDefiniteAssignments().put(sName, Assignment.Unassigned);
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
                : ctxOuter.getVar(sName, name, errs);
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
        assert !getNameMap().containsKey(sName) || isReservedName(sName);

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
        Assignment asn = getVarAssignment(sName);
        if (asn != null)
            {
            return asn.isDefinitelyAssigned();
            }

        // the only other readable variable names are reserved variables, and we need to ask
        // the containing context whether those are readable
        return isReservedName(sName) && getOuterContext().isVarReadable(sName);
        }

    /**
     * Mark the specified variable as being read from within this context.
     *
     * @param sName  the variable name
     */
    public final void markVarRead(String sName)
        {
        markVarRead(false, sName, null, null);
        }

    /**
     * Mark the specified variable as being read from within this context.
     *
     * @param tokName     the variable name as a token from the source code
     * @param errs        the error list to log to
     */
    public final void markVarRead(Token tokName, ErrorListener errs)
        {
        markVarRead(false, tokName.getValueText(), tokName, errs);
        }

    // TODO need separate "mark var required"

    /**
     * Mark the specified variable as being read from within this context.
     *
     * @param fNested  true if the variable is being read from within a context nested within
     *                 this context
     * @param sName    the variable name
     * @param tokName  the variable name as a token from the source code (optional)
     * @param errs     the error list to log to (optional)
     */
    protected void markVarRead(boolean fNested, String sName, Token tokName, ErrorListener errs)
        {
        if (fNested || isVarReadable(sName))
            {
            if (!isVarDeclaredInThisScope(sName))
                {
                // the idea here is that we will communicate up the chain of contexts that the
                // specified variable is being read, so that (for example) a capture can occur
                // if necessary
                Context ctxOuter = getOuterContext();
                if (ctxOuter != null)
                    {
                    ctxOuter.markVarRead(true, sName, tokName, errs);
                    }
                }
            }
        else
            {
            if (tokName != null && errs != null)
                {
                if (isReservedName(sName))
                    {
                    tokName.log(errs, getSource(), Severity.ERROR,
                            sName.startsWith("this") ? Compiler.NO_THIS     :
                            sName.equals("super")    ? Compiler.NO_SUPER    :
                                                       Compiler.NAME_MISSING, sName);

                    // add the variable to the reserved names that are allowed, to avoid
                    // repeating the same error logging
                    setVarAssignment(sName, Assignment.AssignedOnce);
                    }
                else
                    {
                    tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_UNASSIGNED, sName);

                    // record that the variable is definitely assigned so that the error will
                    // not be repeated unnecessarily within this context
                    setVarAssignment(sName, getVarAssignment(sName).applyAssignment());
                    }
                }
            else
                {
                throw new IllegalStateException("illegal var read: name=" + sName);
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
            Argument arg = getVar(sName);
            return arg instanceof Register && ((Register) arg).isWritable();
            }

        // we don't actually explicitly check for reserved names, but this has the effect of
        // reporting them as "not writable" by walking up to the root and then rejecting them
        // (since no context will answer "yes" to this question along the way)
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
    public final void markVarWrite(Token tokName, ErrorListener errs)
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
     * within this context", false indicating "assigned once within this context", and true
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
        return hasInitialNames()
                ? ensureNameMap()
                : m_mapByName == null
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
            if (hasInitialNames())
                {
                initNameMap(mapByName);
                }
            m_mapByName = mapByName;
            }

        return mapByName;
        }

    /**
     * @return true iff the context has to register initial names
     */
    protected boolean hasInitialNames()
        {
        return false;
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
     * Determine if the specified name refers to a reserved name.
     *
     * @param sName  the name to test
     *
     * @return true iff the name is a reserved name
     */
    public boolean isReservedName(String sName)
        {
        switch (sName)
            {
            case "this":
            case "this:target":
            case "this:public":
            case "this:protected":
            case "this:private":
            case "this:struct":
            case "this:service":
            case "super":
            case "this:module":
                return true;

            default:
                return false;
            }
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
     * A nested context for a "when false" or "when true" fork.
     */
    public static class ForkedContext
            extends Context
        {
        public ForkedContext(Context ctxOuter, boolean fWhenTrue)
            {
            super(ctxOuter);
            m_fWhenTrue = fWhenTrue;
            }

        public boolean isWhenTrue()
            {
            return m_fWhenTrue;
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = super.getVarAssignment(sName);
            if (!getDefiniteAssignments().containsKey(sName))
                {
                // the variable assignment came from outside of (i.e. before) this fork
                asn = isWhenTrue() ? asn.whenTrue() : asn.whenFalse();
                }
            return asn;
            }

        @Override
        protected void promoteAssignment(String sName, Assignment asnInner)
            {
            Context    ctxOuter = getOuterContext();
            Assignment asnOuter = ctxOuter.getVarAssignment(sName);
            Assignment asnJoin  = asnOuter.join(asnInner, isWhenTrue());
            ctxOuter.setVarAssignment(sName, asnJoin);
            }

        private boolean m_fWhenTrue;
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


    // ----- data members --------------------------------------------------------------------------

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
     * Each variable declared within this context is registered in this map, along with the
     * Argument that represents it.
     */
    private Map<String, Argument> m_mapByName;

    /**
     * Each variable assigned within this context is registered in this map. The boolean value
     * represents multiple assignments.
     */
    private Map<String, Assignment> m_mapAssigned;
    }
