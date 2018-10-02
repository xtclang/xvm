package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Assignment;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * Compiler context for compiling a method body.
 */
public class Context
    {
    /**
     * Construct a Context.
     *
     * @param ctxOuter  the context that this Context is nested within
     */
    protected Context(Context ctxOuter, boolean fDemuxOnExit)
        {
        m_ctxOuter     = ctxOuter;
        m_fDemuxOnExit = fDemuxOnExit;
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
     * Associate an AST node with this Context, for example if the node is able to ground a
     * short-circuit or act as a "break" target.
     *
     * @param node  the AST node that this Context corresponds to in terms of scope and completion
     */
    public void registerNode(AstNode node)
        {
        assert m_node == null;
        m_node = node;
        }

    /**
     * @return the AST node associated with this Context, or null if none is explicitly associated
     */
    public AstNode getNode()
        {
        return m_node;
        }

    /**
     * Used in the validation phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context enter()
        {
        return new Context(this, true);
        }

    /**
     * Used in the validation phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context enterBlackhole()
        {
        return new BlackholeContext(this);
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
    public Context enterFork(boolean fWhenTrue)
        {
        return new ForkedContext(this, fWhenTrue);
        }

    /**
     * Create a short-circuiting "and" context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new "and" context
     */
    public Context enterAnd()
        {
        return new AndContext(this);
        }

    /**
     * Create a short-circuiting "or" context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new "or" context
     */
    public Context enterOr()
        {
        return new OrContext(this);
        }

    /**
     * Create a negated form of this context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new (negating) context
     */
    public Context enterNot()
        {
        return new NotContext(this);
        }

    /**
     * Create a context that tracks variable assignment data within a loop. The assignments within
     * a loop are assumed to be <i>at least once</i>; entering a forked context before or after
     * entering the loop context allows a <i>zero or more times</i> loop to be constructed.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new (forked) context
     */
    public Context enterLoop()
        {
        return new LoopingContext(this);
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
        return new InferringContext(this, typeLeft);
        }

    /**
     * Mark the Context as being non-completing from this point forward. This means that the code
     * for which the context exists has completed abruptly.
     */
    public void markNonCompleting()
        {
        m_fNonCompleting = true;
        }

    /**
     * @return true iff the context has transitioned into an "unreachable" mode due to a
     *         non-completing or abruptly completing statement or expression
     */
    public boolean isCompleting()
        {
        return !m_fNonCompleting;
        }

    /**
     * Exit the scope that was created by calling {@link #enter()}. Used in the validation
     * phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context exit()
        {
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
                Assignment asnOuter = promote(sName, asnInner);
                if (m_fDemuxOnExit)
                    {
                    asnOuter = asnOuter.demux();
                    }
                getOuterContext().setVarAssignment(sName, asnOuter);
                }
            }

        return getOuterContext();
        }

    // TODO public void shortTo(Context ctxOuter)
    protected Map<String, Assignment> prepareExit(Context ctxOuter)
        {

        Map<String, Assignment> mapMods = getDefiniteAssignments();
        if (mapMods.isEmpty())
            {
            return Collections.EMPTY_MAP;
            }

        Map<String, Assignment> mapPrep = new HashMap<>();
        for (Entry<String, Assignment> entry : mapMods.entrySet())
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
                Assignment asnOuter = promote(sName, asnInner);
                //if (fDemux)
                    {
                    asnOuter = asnOuter.demux();
                    }
                getOuterContext().setVarAssignment(sName, asnOuter);
                }
            }
        return mapPrep;
        }

    /**
     * Promote assignment information from this context to its enclosing context.
     *
     * @param sName     the variable name
     * @param asnInner  the variable assignment information to promote to the enclosing context
     *
     * @return the promoted assignment information
     */
    protected Assignment promote(String sName, Assignment asnInner)
        {
        return asnInner;
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
        // TODO if (isCompleting())

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

    protected void collectVariables(Set<String> setVars)
        {
        Context ctxOuter = getOuterContext();
        if (ctxOuter != null)
            {
            ctxOuter.collectVariables(setVars);
            }

        setVars.addAll(getDefiniteAssignments().keySet());
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("Current: ");
        Map<String, Assignment> mapVars = getDefiniteAssignments();
        if (mapVars.isEmpty())
            {
            sb.append("none");
            }
        else
            {
            TreeSet<String> setVars = new TreeSet<>(mapVars.keySet());
            boolean fFirst = true;
            for (String s : setVars)
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    sb.append(", ");
                    }

                sb.append(s)
                  .append("=")
                  .append(getVarAssignment(s));
                }
            }

        TreeSet<String> setVars = new TreeSet<>();
        collectVariables(setVars);
        if (setVars.size() > mapVars.size())
            {
            sb.append("; all variables:");
            for (String s : setVars)
                {
                sb.append('\n')
                  .append(s)
                  .append('=')
                  .append(getVarAssignment(s));
                }
            }

        return sb.toString();
        }


    // ----- inner class: BlackholeContext ---------------------------------------------------------

    /**
     * A context that can be used to validate without allowing any mutating operations to leak
     * through to the underlying context.
     */
    protected static class BlackholeContext
            extends Context
        {
        /**
         * Construct a BlackholeContext around an actual context.
         *
         * @param ctxOuter  the actual context
         */
        public BlackholeContext(Context ctxOuter)
            {
            super(ctxOuter, false);
            }

        @Override
        public Context exit()
            {
            // no-op (don't push data up to outer scope)

            return super.getOuterContext();
            }
        }


    // ----- inner class: ForkedContext ------------------------------------------------------------

    /**
     * A nested context for a "when false" or "when true" fork.
     */
    public static class ForkedContext
            extends Context
        {
        public ForkedContext(Context ctxOuter, boolean fWhenTrue)
            {
            super(ctxOuter, false);
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
        protected Assignment promote(String sName, Assignment asnInner)
            {
            Context    ctxOuter = getOuterContext();
            Assignment asnOuter = ctxOuter.getVarAssignment(sName);
            Assignment asnJoin  = asnOuter.join(asnInner, isWhenTrue());
            return asnJoin;
            }

        private boolean m_fWhenTrue;
        }


    // ----- inner class: AndContext ------------------------------------------------------------

    /**
     * A nested context for handling "&&" expressions.
     */
    public static class AndContext
            extends Context
        {
        public AndContext(Context ctxOuter)
            {
            super(ctxOuter, false);
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = super.getVarAssignment(sName);
            return getDefiniteAssignments().containsKey(sName)
                    ? asn
                    : asn.whenTrue(); // "&&" only uses the true branch of the left side
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner)
            {
            // the "when false" portion of this context is combined with the the "when false"
            // portion of the outer context;  the "when true" portion of this context replaces the
            // "when true" portion of the outer context
            Context    ctxOuter = getOuterContext();
            Assignment asnOuter = ctxOuter.getVarAssignment(sName);
            Assignment asnFalse = Assignment.join(asnOuter.whenFalse(), asnInner.whenFalse());
            Assignment asnJoin  = Assignment.join(asnFalse, asnInner.whenTrue());
            return asnJoin;
            }
        }


    // ----- inner class: OrContext ------------------------------------------------------------

    /**
     * A nested context for handling "||" expressions.
     */
    public static class OrContext
            extends Context
        {
        public OrContext(Context ctxOuter)
            {
            super(ctxOuter, false);
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = super.getVarAssignment(sName);
            return getDefiniteAssignments().containsKey(sName)
                    ? asn
                    : asn.whenFalse(); // "||" only uses the false branch of the left side
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner)
            {
            // the "when false" portion of this context replaces the "when false" portion of the
            // outer context;  the "when true" portion of this context is combined with the the
            // "when true" portion of the outer context
            Context    ctxOuter = getOuterContext();
            Assignment asnOuter = ctxOuter.getVarAssignment(sName);
            Assignment asnTrue  = Assignment.join(asnOuter.whenTrue(), asnInner.whenTrue());
            Assignment asnJoin  = Assignment.join(asnInner.whenFalse(), asnTrue);
            return asnJoin;
            }
        }


    // ----- inner class: NotContext ------------------------------------------------------------

    /**
     * A nested context for swapping "when false" and "when true".
     */
    public static class NotContext
            extends Context
        {
        public NotContext(Context ctxOuter)
            {
            super(ctxOuter, false);
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = super.getVarAssignment(sName);
            return getDefiniteAssignments().containsKey(sName)
                    ? asn
                    : asn.negate(); // the variable assignment came from outside of this negation
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner)
            {
            return asnInner.negate();
            }
        }


    // ----- inner class: LoopingContext ------------------------------------------------------------

    /**
     * A nested context for a loop.
     */
    public static class LoopingContext
            extends Context
        {
        public LoopingContext(Context ctxOuter)
            {
            super(ctxOuter, true);
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner)
            {
            Context    ctxOuter = getOuterContext();
            Assignment asnOuter = ctxOuter.getVarAssignment(sName);
            Assignment asnJoin  = asnOuter.joinLoop(asnInner);
            return asnJoin;
            }
        }


    // ----- inner class: InferringContext ------------------------------------------------------------

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
            extends Context
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
            super(ctxOuter, true);

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
     * The node (a Statement or Expression) that this context is associated with.
     */
    private AstNode m_node;

    /**
     * True means that this context should demux the assignment information that it pushes
     * "outwards" on exit.
     */
    private boolean m_fDemuxOnExit;

    /**
     * Set to true when the context has passed the point of completion, i.e. passed any "reachable"
     * code. This could be caused by a "throw", "return", "break" or other abruptly completing
     * construct.
     */
    private boolean m_fNonCompleting;

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
