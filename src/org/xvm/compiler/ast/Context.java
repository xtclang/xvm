package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Assignment;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.MoveThis;

import org.xvm.compiler.ast.StatementBlock.TargetInfo;

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
     * @param ctxOuter      the context that this Context is nested within
     * @param fDemuxOnExit  true if this context should demux the assignment information
     */
    protected Context(Context ctxOuter, boolean fDemuxOnExit)
        {
        m_ctxOuter     = ctxOuter;
        m_fDemuxOnExit = fDemuxOnExit;
        m_fReachable   = ctxOuter == null || ctxOuter.isReachable();
        }

    /**
     * @return the outer context
     */
    protected Context getOuterContext()
        {
        return m_ctxOuter;
        }

    /**
     * @return true iff this context demuxes assignment data as it pushes it outwards
     */
    protected boolean isDemuxing()
        {
        return m_fDemuxOnExit;
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
     *         "this" and "this:struct" and "this:class" are available, but other reserved registers
     *         that require an instance of the class are not available
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
     * @return the generic type resolver based on the data in this context
     */
    public GenericTypeResolver getFormalTypeResolver()
        {
        return sFormalName ->
            {
            Argument arg = getVar(sFormalName);
            if (arg == null)
                {
                return isFunction()
                        ? null
                        : getThisType().resolveGenericType(sFormalName);
                }

            // During the LambdaExpression validation, a LambdaContext/CaptureContext collects the
            // used variables by intercepting markVarRead() and creates all the necessary captures
            // based on that information.
            // However, a formal type resolution based on method's type parameters may use them only
            // implicitly, thus preventing the context to register/collect a corresponding type
            // parameter (see the lambda in List.bubbleSort() function as an example);
            // hence the compensation below
            if (arg instanceof Register)
                {
                Register reg = (Register) arg;
                if (!reg.isUnknown() && getMethod().isTypeParameter(reg.getIndex()))
                    {
                    markVarRead(sFormalName);
                    }
                }

            TypeConstant typeType = arg.getType();
            assert typeType.isTypeOfType();
            return typeType.getParamType(0);
            };
        }

    /**
     * @return the ConstantPool
     */
    public ConstantPool pool()
        {
        return getOuterContext().pool();
        }


    // ----- nested context creation ---------------------------------------------------------------

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
     * Create a nested "if" of this context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new "if" context
     */
    public Context enterIf()
        {
        return new IfContext(this);
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
     * <p/>
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
     * Create a delegating context that allows this context to resolve names for elements in a list.
     * <p/>
     * As a result, it allows us to write:
     * <pre><code>
     *    FileChannel open(ReadOption read=Read, WriteOption... write=[Write]);
     * </code></pre>
     *  instead of
     * <pre><code>
     *    FileChannel open(ReadOption read=Read, WriteOption... write=[WriteOption.Write]);
     * </code></pre>
     *
     * @return a new context
     */
    public Context enterList()
        {
        return new Context(this, true);
        }

    /**
     * @return true iff the code for which the context exists is considered reachable
     */
    public boolean isReachable()
        {
        return m_fReachable;
        }

    /**
     * This method allows the code for which the Context exists to be marked as reachable vs
     * unreachable, for the current point in the code.
     *
     * @param fReachable  true iff the current point in the code can be reached
     */
    public void setReachable(boolean fReachable)
        {
        m_fReachable = fReachable;
        }

    /**
     * Exit the scope that was created by calling {@link #enter()}. Used in the validation
     * phase to track scopes.
     * <p/>
     * Note: This can only be used during the validate() stage.
     */
    public Context exit()
        {
        Context ctxOuter   = getOuterContext();
        boolean fCompletes = isReachable();
        boolean fDemuxing  = isDemuxing();

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
                // TODO if fCompletes || this is lambda???
                // REVIEW CP this prevents "return" statement in lambda to promote the assignments
                // if (fCompleting)
                {
                Assignment asnOuter = promote(sName, asnInner, ctxOuter.getVarAssignment(sName));
                if (fDemuxing)
                    {
                    asnOuter = asnOuter.demux();
                    }
                ctxOuter.setVarAssignment(sName, asnOuter);
                }
            }

        if (fCompletes)
            {
            for (Entry<String, Argument> entry : getNameMap().entrySet())
                {
                promoteNarrowedType(entry.getKey(), entry.getValue(), Branch.Always);
                }
            for (Entry<String, Argument> entry : getNarrowingMap(true).entrySet())
                {
                promoteNarrowedType(entry.getKey(), entry.getValue(), Branch.WhenTrue);
                }
            for (Entry<String, Argument> entry : getNarrowingMap(false).entrySet())
                {
                promoteNarrowedType(entry.getKey(), entry.getValue(), Branch.WhenFalse);
                }

            for (Entry<String, TypeConstant> entry : getGenericTypeMap(Branch.Always).entrySet())
                {
                promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.Always);
                }
            for (Entry<String, TypeConstant> entry : getGenericTypeMap(Branch.WhenTrue).entrySet())
                {
                promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.WhenTrue);
                }
            for (Entry<String, TypeConstant> entry : getGenericTypeMap(Branch.WhenFalse).entrySet())
                {
                promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.WhenFalse);
                }
            }
        else
            {
            ctxOuter.promoteNonCompleting(this);
            }

        return ctxOuter;
        }

    /**
     * Discard the context without promoting its contents.
     */
    public void discard()
        {
        m_fReachable = false;
        m_ctxOuter   = null;
        }

    /**
     * Determine the effects of an abrupt exit from this context to the specified context.
     *
     * @param ctxDest  the context (somewhere in the context tree at or above this context) that is
     *                 being transitioned to
     *
     * @return the variable assignment contributions that need to be made if the code exits abruptly
     *         at this point and breaks/continues/short-circuits to the specified context
     */
    public Map<String, Assignment> prepareJump(Context ctxDest)
        {
        // don't pollute a reachable destination with assignments from an unreachable point in code
        if (!this.isReachable() && ctxDest.isReachable())
            {
            return Collections.emptyMap();
            }

        // begin with a snap-shot of the current modifications
        Map<String, Assignment> mapMods = new HashMap<>();
        boolean                 fDemux  = false;

        Context ctxInner = this;
        while (ctxInner != ctxDest)
            {
            // calculate impact of the already-accumulated assignment deltas across this context
            // boundary
            for (Iterator<String> iter = mapMods.keySet().iterator(); iter.hasNext(); )
                {
                String sName = iter.next();
                if (ctxInner.isVarDeclaredInThisScope(sName))
                    {
                    // that variable doesn't exist where we're going
                    iter.remove();
                    }
                }

            // collect all of the other pending modifications that will be promoted to the outer
            // context
            for (String sName : ctxInner.getDefiniteAssignments().keySet())
                {
                if (!mapMods.containsKey(sName) && !ctxInner.isVarDeclaredInThisScope(sName))
                    {
                    mapMods.put(sName, getVarAssignment(sName));
                    }
                }

            fDemux  |= ctxInner.isDemuxing();
            ctxInner = ctxInner.getOuterContext();
            }

        if (fDemux)
            {
            for (Entry<String, Assignment> entry : mapMods.entrySet())
                {
                entry.setValue(entry.getValue().demux());
                }
            }

        return mapMods;
        }

    /**
     * Merge a previously prepared set of variable assignment information into this context.
     *
     * @param mapAdd  a result from a previous call to {@link #prepareJump}
     */
    public void merge(Map<String, Assignment> mapAdd)
        {
        Map<String, Assignment> mapAsn = ensureDefiniteAssignments();
        if (isReachable())
            {
            for (Entry<String, Assignment> entry : mapAdd.entrySet())
                {
                String     sName  = entry.getKey();
                Assignment asnNew = entry.getValue();
                Assignment asnOld = getVarAssignment(sName);
                mapAsn.put(sName, asnOld.join(asnNew));
                }
            }
        else
            {
            mapAsn.putAll(mapAdd);
            setReachable(true);
            }
        }

    /**
     * Promote assignment information from this context to its enclosing context.
     *
     * @param sName     the variable name
     * @param asnInner  the variable assignment information to promote to the enclosing context
     * @param asnOuter  the variable assignment information from the enclosing context
     *
     * @return the promoted assignment information
     */
    protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
        {
        return asnInner;
        }

    /**
     * Promote narrowing type information for the specified argument from this context to its
     * enclosing context.
     *
     * @param sName   the variable name
     * @param arg     the corresponding narrowed argument
     * @param branch  the branch this narrowing comes from
     */
    protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
        {
        if (branch == Branch.Always && !isVarDeclaredInThisScope(sName))
            {
            getOuterContext().replaceArgument(sName, branch, arg);
            }
        }

    /**
     * Promote narrowing type information for the specified generic type from this context to its
     * enclosing context.
     *
     * @param sName       the generic type name
     * @param typeNarrow  the corresponding narrowed type
     * @param branch      the branch this narrowing comes from
     */
    protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrow, Branch branch)
        {
        if (branch == Branch.Always)
            {
            getOuterContext().replaceGenericType(sName, branch, typeNarrow);
            }
        }

    /**
     * Promote the non-completing state from the specified context to this one.
     *
     * @param ctxInner  the inner context that is non-completing
     */
    protected void promoteNonCompleting(Context ctxInner)
        {
        setReachable(false);
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
        if (isVarDeclaredInThisScope(sName) || !isVarHideable(sName))
            {
            tokName.log(errs, getSource(), Severity.ERROR, Compiler.VAR_DEFINED, sName);
            }

        ensureNameMap().put(sName, reg);
        ensureDefiniteAssignments().put(sName, reg.isPredefined() ? Assignment.AssignedOnce
                                                                  : Assignment.Unassigned);
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
        return getVar(sName, null, Branch.Always, null);
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
        return getVar(name.getValueText(), name, Branch.Always, errs);
        }

    /**
     * Internal implementation of getVar() that allows the lookup to be done with or without a
     * token.
     *
     * @param sName  the name to look up
     * @param name   the token to use for error reporting (optional)
     * @param branch the branch to look at
     * @param errs   the error list to use for error reporting (optional)
     *
     * @return the argument for the variable, or null
     */
    protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
        {
        Argument arg = getLocalVar(sName, branch);
        if (arg == null)
            {
            Context ctxOuter = getOuterContext();
            if (ctxOuter != null)
                {
                arg = ctxOuter.getVar(sName, name, Branch.Always, errs);
                if (arg instanceof Register)
                    {
                    arg = resolveRegisterType(branch, (Register) arg);
                    }
                }
            }
        return arg;
        }

    /**
     * Check for the variable definition in this context.
     *
     * @param sName  the name to look up
     * @param branch the branch to look at
     *
     * @return the argument for the variable, or null
     */
    protected Argument getLocalVar(String sName, Branch branch)
        {
        Argument arg;
        switch (branch)
            {
            case WhenTrue:
                arg = getNarrowingMap(true).get(sName);
                break;

            case WhenFalse:
                arg = getNarrowingMap(false).get(sName);
                break;

            default:
                arg = null;
                break;
            }

        return arg == null
                ? getNameMap().get(sName)
                : arg;
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
        Argument arg = getNameMap().get(sName);
        return arg instanceof Register && ((Register) arg).isInPlace();
        }

    /**
     * @return true iff this context declares any names
     */
    public boolean isAnyVarDeclaredInThisScope()
        {
        for (String sName : getNameMap().keySet())
            {
            if (isVarDeclaredInThisScope(sName))
                {
                return true;
                }
            }
        return false;
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
        assert !isVarDeclaredInThisScope(sName);

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
            Argument arg = getVar(sName);

            if (!asn.isDefinitelyAssigned())
                {
                // DVar is always readable (TODO: ensure the "get" is overridden)
                return arg instanceof Register && ((Register) arg).isDVar();
                }

            return !(arg instanceof Register) || ((Register) arg).isReadable();
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
                                                       Compiler.NAME_MISSING,
                            sName, getMethod().getIdentityConstant().getValueString());

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
        if (sName.equals("$"))
            {
            return false;
            }

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
     * Mark an AstNode at the specified position as reliant on "this".
     *
     * @param lPos  the node's position
     * @param errs  the error list to log to (optional)
     */
    public void requireThis(long lPos, ErrorListener errs)
        {
        getOuterContext().requireThis(lPos, errs);
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
     * Mark the specified generic type as being used within this context.
     *
     * @param sName  the generic type name
     * @param errs   the error list to log to (optional)
     */
    public void useGenericType(String sName, ErrorListener errs)
        {
        Context ctxOuter = getOuterContext();
        if (ctxOuter != null)
            {
            ctxOuter.useGenericType(sName, errs);
            }
        }

    /**
     * Determine if a variable of the specified name can be introduced.
     *
     * @param sName  the variable name to introduce
     *
     * @return true iff it is legal to introduce a variable of that name
     */
    public boolean isVarHideable(String sName)
        {
        if (sName.equals("$"))
            {
            return true;
            }

        return !(getVar(sName) instanceof Register);
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
        Argument arg = getVar(sName, name, Branch.Always, errs);
        if (arg == null)
            {
            arg = resolveReservedName(sName, name, errs);
            if (arg == null)
                {
                arg = resolveRegularName(this, sName, name, errs);
                }
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
        return resolveRegularName(this, name.getValueText(), name, errs);
        }

    /**
     * Resolve a name (other than a reserved name) to an argument.
     *
     * @param ctxFrom  the context from which the name resolution began
     * @param sName    the name to resolve
     * @param name     the name token for error reporting (optional)
     * @param errs     the error list to log errors to (optional)
     *
     * @return an Argument iff the name is registered to an argument; otherwise null
     */
    protected Argument resolveRegularName(Context ctxFrom, String sName, Token name, ErrorListener errs)
        {
        return getOuterContext().resolveRegularName(ctxFrom, sName, name, errs);
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
            case "this:class":
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
     * Narrow the type of the specified variable in this context for the specified branch.
     */
    protected void narrowLocalRegister(String sName, Register reg,
                                       Branch branch, TypeConstant typeNarrow)
        {
        assert typeNarrow.isA(reg.getType()) || typeNarrow.isA(reg.getOriginalType());

        replaceArgument(sName, branch, reg.narrowType(typeNarrow));
        }

    /**
     * Replace the existing argument with the specified one for the given branch.
     */
    protected void replaceArgument(String sName, Branch branch, Argument argNew)
        {
        if (branch == Branch.Always)
            {
            if (argNew instanceof Register && isVarDeclaredInThisScope(sName))
                {
                // the narrowing register is replacing a local register; remember that fact
                ((Register) argNew).markInPlace();
                }
            ensureNameMap().put(sName, argNew);
            }
        else
            {
            ensureNarrowingMap(branch == Branch.WhenTrue).put(sName, argNew);
            }
        }

    /**
     * Replace (narrow) the generic type of the specified name in this context with the specified
     * target's type.
     */
    protected void replaceGenericArgument(String sName, Branch branch, TargetInfo infoNew)
        {
        // place the info with the narrowed generic type (used by NamedTypeExpression)
        switch (branch)
            {
            case WhenTrue:
                ensureNarrowingMap(true).put(sName, infoNew);

            case WhenFalse:
                ensureNarrowingMap(false).put(sName, infoNew);

            default:
                ensureNameMap().put(sName, infoNew);
            }

        replaceGenericType(sName, branch, infoNew.getType());
        }

    /**
     * Replace (narrow) the generic type of the specified name in this context.
     */
    protected void replaceGenericType(String sName, Branch branch, TypeConstant typeNew)
        {
        assert typeNew.isTypeOfType();

        ensureGenericTypeMap(branch).put(sName, typeNew);
        }

    /**
     * Narrow the type of the specified property in this context for the specified branch.
     */
    protected void narrowProperty(String sName, PropertyConstant idProp,
                                  Branch branch, Argument argNarrow)
        {
        assert argNarrow.getType().isA(idProp.getType());

        replaceArgument(sName, branch, argNarrow);
        }

    /**
     * Merge the types of the existing argument with the specified one for the given branch.
     */
    protected void joinArgument(String sName, Branch branch, Argument argNew)
        {
        Map<String, Argument> map = branch == Branch.Always
            ? ensureNameMap()
            : ensureNarrowingMap(branch == Branch.WhenTrue);

        Argument argOld = map.get(sName);
        if (argOld != null)
            {
            if (argOld instanceof Register)
                {
                TypeConstant typeOld = argOld.getType();
                TypeConstant typeNew = argNew.getType();

                TypeConstant typeJoin = typeNew.intersect(pool(), typeOld);
                map.put(sName, ((Register) argOld).narrowType(typeJoin));
                }
            else
                {
                map.remove(sName);
                }
            }
        }

    /**
     * Merge the types of the existing argument with the specified one for the given branch.
     */
    protected void joinGenericType(String sName, Branch branch, Argument argNew)
        {
        Map<String, TypeConstant> map = ensureGenericTypeMap(branch);

        TypeConstant typeOld = map.get(sName);
        if (typeOld != null)
            {
            TypeConstant typeNew = argNew.getType();

            TypeConstant typeJoin = typeNew.intersect(pool(), typeOld);
            map.put(sName, typeJoin);
            }
        else
            {
            map.remove(sName);
            }
        }

    /**
     * @return the read-only map that provides a name-to-narrowed type lookup
     */
    protected Map<String, Argument> getNarrowingMap(boolean fWhenTrue)
        {
        Map<String, Argument> map = fWhenTrue ? m_mapWhenTrue : m_mapWhenFalse;
        return map == null ? Collections.EMPTY_MAP : map;
        }

    /**
     * @return the map that provides a name-to-narrowed type lookup
     */
    protected Map<String, Argument> ensureNarrowingMap(boolean fWhenTrue)
        {
        Map<String, Argument> map = fWhenTrue ? m_mapWhenTrue : m_mapWhenFalse;

        if (map == null)
            {
            map = new HashMap<>();
            if (fWhenTrue)
                {
                m_mapWhenTrue = map;
                }
            else
                {
                m_mapWhenFalse = map;
                }
            }

        return map;
        }

    /**
     * @return the read-only map that provides a name-to-narrowed generic type lookup
     */
    protected Map<String, TypeConstant> getGenericTypeMap(Branch branch)
        {
        Map<String, TypeConstant> map;
        switch (branch)
            {
            case WhenTrue:
                map = m_mapGenericWhenTrue;
                break;

            case WhenFalse:
                map = m_mapGenericWhenFalse;
                break;

            default:
                map = m_mapGeneric;
                break;
            }
        return map == null ? Collections.EMPTY_MAP : map;
        }

    /**
     * @return the map that provides a name-to-narrowed generic type lookup
     */
    protected Map<String, TypeConstant> ensureGenericTypeMap(Branch branch)
        {
        Map<String, TypeConstant> map = getGenericTypeMap(branch);

        if (map == Collections.EMPTY_MAP)
            {
            switch (branch)
                {
                case WhenTrue:
                    return m_mapGenericWhenTrue = new HashMap<>();

                case WhenFalse:
                    return m_mapGenericWhenFalse = new HashMap<>();

                default:
                    return m_mapGeneric = new HashMap<>();
                }
            }
        return map;
        }

    /**
     * @return a generic type resolver for a given branch; null if no narrowing info exists
     */
    protected GenericTypeResolver getLocalResolver(Branch branch)
        {
        Map<String, TypeConstant> map = getGenericTypeMap(branch);

        return map == Collections.EMPTY_MAP
                ? null
                : sFormalName ->
                    {
                    TypeConstant typeType = map.get(sFormalName);
                    if (typeType == null)
                        {
                        return null;
                        }
                    assert typeType.isTypeOfType();
                    return typeType.getParamType(0);
                    };
        }

    /**
     * Resolve the specified register's type on the specified branch.
     *
     * @return a potentially narrowed register
     */
    protected Argument resolveRegisterType(Branch branch, Register reg)
        {
        GenericTypeResolver resolver = getLocalResolver(branch);
        if (resolver != null)
            {
            TypeConstant typeOriginal = reg.getType();
            TypeConstant typeResolved = typeOriginal.resolveGenerics(pool(), resolver);

            if (typeResolved != typeOriginal)
                {
                return reg.narrowType(typeResolved);
                }
            }
        return reg;
        }

    /**
     * Collect all the variable names that are "visible" in this context.
     */
    protected void collectVariables(Set<String> setVars)
        {
        Context ctxOuter = getOuterContext();
        if (ctxOuter != null)
            {
            ctxOuter.collectVariables(setVars);
            }

        setVars.addAll(getDefiniteAssignments().keySet());
        }

    /**
     * Calculate the number of steps ("this.outer") it takes to get from this context to the
     * specified parent.
     *
     * @param clzParent  the parent class
     *
     * @return the number of steps or -1 if the parent is not in the path
     */
    public int getStepsToOuterClass(ClassStructure clzParent)
        {
        IdentityConstant idParent  = clzParent.getIdentityConstant();
        Component        component = getMethod().getParent().getParent(); // namespace
        int              cSteps    = 0;

        while (component != null)
            {
            IdentityConstant id = component.getIdentityConstant();

            switch (id.getFormat())
                {
                case Class:
                    {
                    if (id.equals(idParent))
                        {
                        return cSteps;
                        }

                    ClassStructure clz = (ClassStructure) component;
                    if (clz.hasContribution(idParent, true))
                        {
                        return cSteps;
                        }

                    if (clz.isStatic() || clz.isTopLevel())
                        {
                        return -1;
                        }

                    cSteps++;
                    break;
                    }

                case Method:
                case MultiMethod:
                    break;

                case Property:
                    {
                    PropertyStructure prop = (PropertyStructure) id.getComponent();
                    if (prop.isRefAnnotated())
                        {
                        cSteps++;
                        }
                    break;
                    }

                case Module:
                case Package:
                    return -1;

                default:
                    throw new IllegalStateException();
                }

            component = component.getParent();
            }

        return -1;
        }

    /**
     * Generate a Register for "this", which may be an "outer" one depending on the context.
     *
     * @param code               the code
     * @param fAllowConstructor  if true, the returned argument may return a "struct"
     *                           and be used in a constructor
     * @param errs               the error listener
     *
     * @return the Register to use for "this" variable
     */
    public Register generateThisRegister(Code code, boolean fAllowConstructor, ErrorListener errs)
        {
        Argument arg = resolveReservedName(fAllowConstructor ? "this" : "this:target", null, errs);
        if (arg instanceof Register)
            {
            return (Register) arg;
            }

        TargetInfo target  = (TargetInfo) arg;
        Register   regTemp = new Register(target.getType(), Op.A_STACK);
        code.add(new MoveThis(target.getStepsOut(), regTemp));
        return regTemp;
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
            return getOuterContext();
            }

        @Override
        public Map<String, Assignment> prepareJump(Context ctxDest)
            {
            return Collections.EMPTY_MAP;
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            throw new IllegalStateException();
            }
        }

    // ----- inner class: IfContext ----------------------------------------------------------------

    /**
     * A custom context implementation to provide type-narrowing as a natural side-effect of an
     * "if" or a "ternary" block with a terminating branch.
     */
    static class IfContext
            extends Context
        {
        public IfContext(Context outer)
            {
            super(outer, true);
            }

        @Override
        public boolean isReachable()
            {
            return m_ctxWhenTrue  != null && m_ctxWhenTrue .m_fReachable
                || m_ctxWhenFalse != null && m_ctxWhenFalse.m_fReachable
                || super.isReachable();
            }

        @Override
        public Context enterFork(boolean fWhenTrue)
            {
            Context ctx = super.enterFork(fWhenTrue);
            if (fWhenTrue)
                {
                m_ctxWhenTrue = ctx;
                }
            else
                {
                m_ctxWhenFalse = ctx;
                }
            return ctx;
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            super.promoteNarrowedType(sName, arg, branch);

            // choose the wider type of the two branches and promote to "Always"
            if (branch == Branch.WhenTrue)
                {
                Argument argTrue  = arg;
                Argument argFalse = getNarrowingMap(false).get(sName);
                if (argFalse != null)
                    {
                    Context      ctxOuter  = getOuterContext();
                    Argument     argOrig   = ctxOuter.getVar(sName);
                    TypeConstant typeOrig  = argOrig == null ? null : argOrig.getType();
                    TypeConstant typeTrue  = argTrue .getType();
                    TypeConstant typeFalse = argFalse.getType();

                    if (typeFalse.isA(typeTrue))
                        {
                        if (!typeTrue.equals(typeOrig))
                            {
                            ctxOuter.replaceArgument(sName, Branch.Always, argTrue);
                            }
                        }
                    else if (typeTrue.isA(typeFalse))
                        {
                        if (!typeFalse.equals(typeOrig))
                            {
                            ctxOuter.replaceArgument(sName, Branch.Always, argFalse);
                            }
                        }
                    }
                }
            }

        @Override
        protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            super.promoteNarrowedGenericType(sName, typeNarrowed, branch);

            // choose the wider type of the two branches and promote to "Always"
            if (branch == Branch.WhenTrue)
                {
                TypeConstant typeTrue  = typeNarrowed;
                TypeConstant typeFalse = getGenericTypeMap(Branch.WhenFalse).get(sName);;
                if (typeFalse != null)
                    {
                    Context      ctxOuter = getOuterContext();
                    TypeConstant typeOrig = ctxOuter.getGenericTypeMap(Branch.Always).get(sName);
                    if (typeFalse.isA(typeTrue))
                        {
                        if (!typeTrue.equals(typeOrig))
                            {
                            ctxOuter.replaceGenericType(sName, Branch.Always, typeTrue);
                            }
                        }
                    else if (typeTrue.isA(typeFalse))
                        {
                        if (!typeFalse.equals(typeOrig))
                            {
                            ctxOuter.replaceGenericType(sName, Branch.Always, typeFalse);
                            }
                        }
                    }
                }
            }

        @Override
        protected void promoteNonCompleting(Context ctxInner)
            {
            discardBranch(ctxInner == m_ctxWhenTrue);
            }

        private void discardBranch(boolean fWhenTrue)
            {
            Map mapBranch;

            // clear this branch
            mapBranch = getNarrowingMap(fWhenTrue);
            if (!mapBranch.isEmpty())
                {
                mapBranch.clear();
                }
            mapBranch = getGenericTypeMap(Branch.of(fWhenTrue));
            if (!mapBranch.isEmpty())
                {
                mapBranch.clear();
                }

            // copy the opposite branch; the context may merge it (see IfContext)
            mapBranch = getNarrowingMap(!fWhenTrue);
            if (!mapBranch.isEmpty())
                {
                ensureNarrowingMap(fWhenTrue).putAll(mapBranch);
                }

            mapBranch = getGenericTypeMap(Branch.of(!fWhenTrue));
            if (!mapBranch.isEmpty())
                {
                ensureGenericTypeMap(Branch.of(fWhenTrue)).putAll(mapBranch);
                }
            }


        private Context m_ctxWhenTrue;
        private Context m_ctxWhenFalse;
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
        protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
            {
            Argument arg = getLocalVar(sName, branch);
            if (arg == null)
                {
                // we only use the parent's "true" branch
                arg = getOuterContext().getVar(sName, name, Branch.of(m_fWhenTrue), errs);
                if (arg instanceof Register)
                    {
                    arg = resolveRegisterType(branch, (Register) arg);
                    }
                }
            return arg;
            }

        @Override
        public Assignment getVarAssignment(String sName)
            {
            Assignment asn = super.getVarAssignment(sName);
            assert asn != null;
            if (!getDefiniteAssignments().containsKey(sName))
                {
                // the variable assignment came from outside of (i.e. before) this fork
                asn = isWhenTrue() ? asn.whenTrue() : asn.whenFalse();
                }
            return asn;
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            return asnOuter.join(asnInner, isWhenTrue());
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            // promote our "always" into the corresponding parent's branch
            if (branch == Branch.Always && !isVarDeclaredInThisScope(sName))
                {
                getOuterContext().replaceArgument(sName, Branch.of(m_fWhenTrue), arg);
                }
            }

        @Override
        protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            // promote our "always" into the corresponding parent's branch
            if (branch == Branch.Always)
                {
                getOuterContext().replaceGenericType(sName, Branch.of(m_fWhenTrue), typeNarrowed);
                }
            }

        private boolean m_fWhenTrue;
        }


    // ----- inner class: AndContext ---------------------------------------------------------------

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
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            // the "when false" portion of this context is combined with the the "when false"
            // portion of the outer context;  the "when true" portion of this context replaces the
            // "when true" portion of the outer context
            Assignment asnFalse = Assignment.join(asnOuter.whenFalse(), asnInner.whenFalse());
            Assignment asnJoin  = Assignment.join(asnFalse, asnInner.whenTrue());
            return asnJoin;
            }

        @Override
        protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
            {
            Argument arg = getLocalVar(sName, branch);
            if (arg == null)
                {
                // we only use the parent's "true" branch
                arg = getOuterContext().getVar(sName, name, Branch.WhenTrue, errs);
                if (arg instanceof Register)
                    {
                    arg = resolveRegisterType(branch, (Register) arg);
                    }
                }
            return arg;
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            super.promoteNarrowedType(sName, arg, branch);

            // promote our "true" into the parent's "true" branch
            // join our "false" with the parent's "false"
            switch (branch)
                {
                case WhenTrue:
                    getOuterContext().replaceArgument(sName, branch, arg);
                    break;

                case WhenFalse:
                    getOuterContext().joinArgument(sName, branch, arg);
                    break;
                }
            }

        @Override
        protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            super.promoteNarrowedGenericType(sName, typeNarrowed, branch);

            // promote our "true" into the parent's "true" branch and
            // join our "false" with the parent's "false"
            switch (branch)
                {
                case WhenTrue:
                case Always:
                    getOuterContext().replaceGenericType(sName, branch, typeNarrowed);
                    break;

                case WhenFalse:
                    getOuterContext().joinGenericType(sName, branch, typeNarrowed);
                    break;
                }
            }
        }


    // ----- inner class: OrContext ----------------------------------------------------------------

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
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            // the "when false" portion of this context replaces the "when false" portion of the
            // outer context;  the "when true" portion of this context is combined with the the
            // "when true" portion of the outer context
            Assignment asnTrue = Assignment.join(asnOuter.whenTrue(), asnInner.whenTrue());
            Assignment asnJoin = Assignment.join(asnInner.whenFalse(), asnTrue);
            return asnJoin;
            }

        @Override
        protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
            {
            Argument arg = getLocalVar(sName, branch);
            if (arg == null)
                {
                // we only use the parent's "false" branch (logical short-circuit)
                arg = getOuterContext().getVar(sName, name, Branch.WhenFalse, errs);
                if (arg instanceof Register)
                    {
                    arg = resolveRegisterType(branch, (Register) arg);
                    }
                }
            return arg;
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            super.promoteNarrowedType(sName, arg, branch);

            // promote our "false" into the parent's "false" branch and
            // join our "true" with the parent's "true"
            switch (branch)
                {
                case WhenFalse:
                    getOuterContext().replaceArgument(sName, branch, arg);
                    break;

                case WhenTrue:
                    getOuterContext().joinArgument(sName, branch, arg);
                    break;
                }
            }

        @Override
        protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            super.promoteNarrowedGenericType(sName, typeNarrowed, branch);

            // promote our "false" into the parent's "false" branch and
            // join our "true" with the parent's "true"
            switch (branch)
                {
                case WhenFalse:
                case Always:
                    getOuterContext().replaceGenericType(sName, branch, typeNarrowed);
                    break;

                case WhenTrue:
                    getOuterContext().joinGenericType(sName, branch, typeNarrowed);
                    break;
                }
            }
        }


    // ----- inner class: NotContext ---------------------------------------------------------------

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
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            return asnInner.negate();
            }

        @Override
        protected Argument getVar(String sName, Token name, Branch branch, ErrorListener errs)
            {
            Argument arg = getLocalVar(sName, branch);
            if (arg == null)
                {
                arg = getOuterContext().getVar(sName, name, branch.complement(), errs);
                if (arg instanceof Register)
                    {
                    arg = resolveRegisterType(branch, (Register) arg);
                    }
                }
            return arg;
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            if (branch == Branch.Always)
                {
                super.promoteNarrowedType(sName, arg, branch);
                }
            else
                {
                getOuterContext().replaceArgument(sName, branch.complement(), arg);
                }
            }

        @Override
        protected void promoteNarrowedGenericType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            if (branch == Branch.Always)
                {
                super.promoteNarrowedGenericType(sName, typeNarrowed, branch);
                }
            else
                {
                getOuterContext().replaceGenericType(sName, branch.complement(), typeNarrowed);
                }
            }
        }


    // ----- inner class: LoopingContext -----------------------------------------------------------

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
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            Assignment asnJoin = asnOuter.joinLoop(asnInner);
            return asnJoin;
            }
        }


    // ----- inner class: InferringContext ---------------------------------------------------------

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
        public Context enterList()
            {
            TypeConstant typeCtx = m_typeLeft;
            TypeConstant typeEl  = typeCtx.isA(pool().typeSequence())
                    ? typeCtx.resolveGenericType("Element")
                    : null;
            return typeEl == null
                    ? new Context(this, true)
                    : new InferringContext(this, typeEl);
            }

        @Override
        protected Argument resolveRegularName(Context ctxFrom, String sName, Token name, ErrorListener errs)
            {
            // hold off on logging the errors from the first attempt until the second attempt fails
            ErrorList errsTemp = new ErrorList(1);

            Argument arg = super.resolveRegularName(ctxFrom, sName, name, errsTemp);
            if (arg != null)
                {
                // first attempt succeeded
                return arg;
                }

            SimpleCollector collector = new SimpleCollector();
            if (m_typeLeft.resolveContributedName(sName, collector) == ResolutionResult.RESOLVED)
                {
                // second attempt succeeded
                return collector.getResolvedConstant();
                }

            if (errsTemp != errs)
                {
                errsTemp.logTo(errs);
                }
            return null;
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            getOuterContext().registerVar(tokName, reg, errs);
            }

        TypeConstant m_typeLeft;
        }


    // ----- inner class: CaptureContext -----------------------------------------------------------

    /**
     * A context for compiling in a scope that can capture local variables from an outer scope.
     * This is used by lambdas (LambdaExpression) and anonymous inner classes (NewExpression).
     */
    public static class CaptureContext
            extends Context
        {
        /**
         * Construct a CaptureContext.
         *
         * @param ctxOuter  the context within which this context is nested
         */
        public CaptureContext(Context ctxOuter)
            {
            super(ctxOuter, true);
            }

        @Override
        public Context exit()
            {
            Context ctxOuter = super.exit();

            // record the registers for the captured variables
            Map<String, Boolean>  mapCapture = ensureCaptureMap();
            Map<String, Register> mapVars    = ensureRegisterMap();
            for (String sName : mapCapture.keySet())
                {
                mapVars.put(sName, (Register) getVar(sName));
                }

            return ctxOuter;
            }

        @Override
        public void requireThis(long lPos, ErrorListener errs)
            {
            captureThis();
            }

        @Override
        protected void markVarRead(boolean fNested, String sName, Token tokName, ErrorListener errs)
            {
            if (!isVarDeclaredInThisScope(sName) && getOuterContext().isVarReadable(sName))
                {
                // capture the variable
                ensureCaptureMap().putIfAbsent(sName, false);
                }

            super.markVarRead(fNested, sName, tokName, errs);
            }

        @Override
        public void useGenericType(String sName, ErrorListener errs)
            {
            super.useGenericType(sName, errs);

            TargetInfo info = (TargetInfo) resolveName(sName);
            assert info != null;
            ensureGenericMap().putIfAbsent(sName, info);
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

        /**
         * Obtain the map of names to registers, if it has been built.
         * <p/>
         * Note: built by exit()
         *
         * @return a non-null map of variable name to Register for all of variables to capture
         */
        public Map<String, Register> ensureRegisterMap()
            {
            Map<String, Register> map = m_mapRegisters;
            if (map == null)
                {
                if (getCaptureMap().isEmpty())
                    {
                    // there are never more capture-registers than there are captures
                    return Collections.EMPTY_MAP;
                    }

                m_mapRegisters = map = new HashMap<>();
                }

            return map;
            }

        /**
         * @return a map of names to generic type info
         */
        public Map<String, TargetInfo> getGenericMap()
            {
            return m_mapGenericInfo == null
                    ? Collections.EMPTY_MAP
                    : m_mapGenericInfo;
            }

        /**
         * Mark the capturing context as requiring a "this" from outside of the context. For a
         * lambda, this makes the lambda result into a method instead of a function. For an
         * anonymous inner class, this makes the resulting class an instance child instead of a
         * static child.
         */
        protected void captureThis()
            {
            m_fCaptureThis = true;
            }

        /**
         * @return true iff the lambda is built as a method (and not as a function) in order to
         *         capture the "this" object reference
         */
        public boolean isThisCaptured()
            {
            return m_fCaptureThis;
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            ensureCaptureMap().put(sName, true);
            return asnOuter.applyAssignmentFromCapture();
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        protected Map<String, Boolean> ensureCaptureMap()
            {
            Map<String, Boolean> map = m_mapCapture;
            if (map == null)
                {
                // use a tree map, as it will keep the captures in alphabetical order, which will
                // help to produce lambdas with a "predictable" signature
                m_mapCapture = map = new TreeMap<>();
                }

            return map;
            }

        /**
         * Obtain the map of names to generic type info, if it has been built.
         * <p/>
         * Note: built by exit()
         *
         * @return a non-null map of variable name to TargetInfo for all generic types to capture
         */
        protected Map<String, TargetInfo> ensureGenericMap()
            {
            Map<String, TargetInfo> map = m_mapGenericInfo;
            if (map == null)
                {
                // use a tree map, to keep the captures in alphabetical order, which will
                // help to produce lambdas with a "predictable" signature
                m_mapGenericInfo = map = new TreeMap<>();
                }

            return map;
            }

        /**
         * A map from variable name to read/write flag (false is read-only, true is read-write) for
         * the variables to capture.
         */
        private Map<String, Boolean> m_mapCapture;

        /**
         * A map from variable name to register, built by exit().
         */
        private Map<String, Register> m_mapRegisters;

        /**
         * A map of generic types, built by useGenericType().
         */
        private Map<String, TargetInfo> m_mapGenericInfo;

        /**
         * Set to true iff "this" is captured.
         */
        private boolean m_fCaptureThis;
        }


    // ----- data members --------------------------------------------------------------------------

    protected enum Branch
        {
        Always, WhenTrue, WhenFalse;

        /**
         * @return a complementary branch for this branch
         */
        public Branch complement()
            {
            switch (this)
                {
                case Always:
                    return Always;

                case WhenTrue:
                    return WhenFalse;

                case WhenFalse:
                    return WhenTrue;

                default:
                    throw new IllegalStateException();
                }
            }

        /**
         * @return a branch for a boolean value
         */
        public static Branch of(boolean f)
            {
            return f ? WhenTrue : WhenFalse;
            }
        }

    /**
     * The outer context. The root context will not have an outer context, and an artificial
     * context may not have an outer context.
     */
    private Context m_ctxOuter;

    /**
     * True means that this context should demux the assignment information that it pushes
     * "outwards" on exit.
     */
    private boolean m_fDemuxOnExit;

    /**
     * True iff the code for which the context exists is considered reachable at this point.
     */
    boolean m_fReachable;

    /**
     * Each variable declared within this context is registered in this map, along with the
     * Argument that represents it.
     */
    private Map<String, Argument> m_mapByName;

    /**
     * Each argument declared within a parent context, may narrow its type in a child context
     * for a "true" branch.
     */
    private Map<String, Argument> m_mapWhenTrue;

    /**
     * Each argument declared within a parent context, may narrow its type in a child context
     * for a "false" branch.
     */
    private Map<String, Argument> m_mapWhenFalse;

    /**
     * Each generic type narrowed within this context is registered in this map, along with the
     * narrowing TypeConstant.
     */
    private Map<String, TypeConstant> m_mapGeneric;

    /**
     * Each generic type declared within a parent context, may narrow its type in a child context
     * for a "true" branch.
     */
    private Map<String, TypeConstant> m_mapGenericWhenTrue;

    /**
     * Each generic type declared within a parent context, may narrow its type in a child context
     * for a "false" branch.
     */
    private Map<String, TypeConstant> m_mapGenericWhenFalse;

    /**
     * Each variable assigned within this context is registered in this map. The corresponding value
     * represents the assignments that may have occurred by this point.
     */
    private Map<String, Assignment> m_mapAssigned;
    }
