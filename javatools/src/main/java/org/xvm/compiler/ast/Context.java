package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.SimpleCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.Assignment;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.RegisterAST;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.AstHolder;
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
     * @return the outer branch to use for {@link #getVar(String, Token, Branch, ErrorListener)}
     *         and {@link #resolveFormalType(TypeConstant, Branch)} implementations
     *         based on the current branch
     */
    protected Branch getOuterBranch(Branch branch)
        {
        return Branch.Always;
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
        Context ctx = getOuterContext();
        return ctx != null && ctx.isMethod();
        }

    /**
     * @return true iff the containing MethodStructure is a function (neither a method nor a
     *         constructor), which means that "this", "super", and other reserved registers are
     *         not available
     */
    public boolean isFunction()
        {
        Context ctx = getOuterContext();
        return ctx != null && ctx.isFunction();
        }

    /**
     * @return true iff the containing MethodStructure is a constructor, which means that
     *         "this" and "this:struct" and "this:class" are available, but other reserved registers
     *         that require an instance of the class are not available
     */
    public boolean isConstructor()
        {
        Context ctx = getOuterContext();
        return ctx != null && ctx.isConstructor();
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
     * @return the identity of the containing ClassStructure for the method
     */
    public IdentityConstant getThisClassId()
        {
        return getThisClass().getIdentityConstant();
        }

    /**
     * @return the formal type for "this" (could be narrowed within this context)
     */
    public TypeConstant getThisType()
        {
        Argument argThis = getVar("this");
        return argThis == null
                ? getThisClass().getFormalType()
                : argThis.getType().removeAccess();
        }

    /**
     * @return the Register for "this"
     */
    public Register getThisRegister()
        {
        Register regThis = m_regThis;
        if (regThis == null)
            {
            TypeConstant typeThis = getThisType();

            // we used to adjust the type for "this" inside the property, but that seems to be
            // unnecessary anymore; leaving the code as a reminder just in case
            //
            // Component parent = getMethod().getParent().getParent();
            //
            // while (parent instanceof MethodStructure)
            //     {
            //     parent = parent.getParent().getParent();
            //     }
            // if (parent instanceof PropertyStructure prop && prop.isRefAnnotated())
            //     {
            //     typeThis = prop.getIdentityConstant().getRefType(typeThis);
            //     }

            ConstantPool pool = pool();
            if (isConstructor())
                {
                TypeConstant typeStruct = pool.ensureAccessTypeConstant(typeThis, Access.STRUCT);
                regThis = new Register(typeStruct, "this", Op.A_STRUCT);
                }
            else
                {
                regThis = new Register(typeThis, "this", Op.A_THIS);
                }

            m_regThis = regThis;
            }
        return regThis;
        }

    /**
     * @return the Register for "this"
     */
    public ExprAST getThisRegisterAST()
        {
        return getThisRegister().getRegisterAST();
        }

    /**
     * @return the AST holder
     */
    public AstHolder getHolder()
        {
        return getOuterContext().getHolder();
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
     * Create a nested "and-if" of this context.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new multi-condition "and-if" context
     */
    public Context enterAndIf()
        {
        return new AndIfContext(this);
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
     * Create a nested context that behaves as a "true" branch of an IfContext and automatically
     * marks the corresponding "false" branch as unreachable.
     * <p/>
     * Note: This can only be used during the validate() stage.
     *
     * @return the new (forked) context
     */
    public Context enterInfiniteLoop()
        {
        ForkedContext ctxFork = (ForkedContext) enterFork(true);
        ctxFork.markExclusive();
        return ctxFork;
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
        Context ctxOuter = getOuterContext();

        promoteAssignments(ctxOuter);

        if (isReachable())
            {
            promoteNarrowedTypes();
            promoteNarrowedGenericTypes();
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
        Context ctxOuter = getOuterContext();
        if (ctxOuter != null)
            {
            ctxOuter.unlink(this);
            }

        m_fReachable = false;
        m_ctxOuter   = null;
        }

    /**
     * A notification to the context that its nested context has been discarded.
     */
    protected void unlink(Context ctxDiscarded)
        {
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

        Map<String, Assignment> mapMods = new HashMap<>();
        prepareJump(ctxDest, mapMods, null);
        return mapMods;
        }

    /**
     * Determine the effects of an abrupt exit from this context to the specified context.
     *
     * @param ctxDest     the context (somewhere in the context tree at or above this context) that
     *                    is being transitioned to
     * @param mapAsnMods  the map to put the variable assignment contributions that need to be made
     *                    if the code exits abruptly at this point and
     *                    breaks/continues/short-circuits to the specified context
     * @param mapArgMods  the map to put the variable argument contributions that need to be made
     */
    public void prepareJump(Context ctxDest, Map<String, Assignment> mapAsnMods,
                            Map<String, Argument> mapArgMods)
        {
        // don't pollute a reachable destination with assignments from an unreachable point in code
        if (!this.isReachable() && ctxDest.isReachable())
            {
            return;
            }

        // begin with a snapshot of the current modifications
        boolean fDemux   = false;
        Context ctxInner = this;
        while (ctxInner != ctxDest)
            {
            // calculate impact of the already-accumulated assignment deltas across this context
            // boundary

            // 1) remove variables that don't exist where we're going
            mapAsnMods.keySet().removeIf(sName -> ctxDest.getVar(sName) == null);

            // 2) collect all other pending modifications that will be promoted to the outer context
            for (String sName : ctxInner.getDefiniteAssignments().keySet())
                {
                if (!mapAsnMods.containsKey(sName) && !ctxInner.isVarDeclaredInThisScope(sName))
                    {
                    mapAsnMods.put(sName, getVarAssignment(sName));
                    }
                }

            fDemux  |= ctxInner.isDemuxing();
            ctxInner = ctxInner.getOuterContext();
            }

        if (fDemux)
            {
            for (Entry<String, Assignment> entry : mapAsnMods.entrySet())
                {
                entry.setValue(entry.getValue().demux());
                }
            }

        if (mapArgMods != null)
            {
            for (String sName : mapAsnMods.keySet())
                {
                mapArgMods.put(sName, getVar(sName));
                }
            }
        }

    /**
     * Merge a previously prepared set of variable assignment information into this context.
     *
     * @param mapAdd  a map of assignments from a previous call to {@link #prepareJump}
     */
    public void merge(Map<String, Assignment> mapAdd)
        {
        merge(mapAdd, Collections.emptyMap());
        }

    /**
     * Merge a previously prepared set of variable assignment information into this context.
     *
     * @param mapAddAsn  a map of assignments from a previous call to {@link #prepareJump}
     * @param mapAddArg  a map of arguments from a previous call to {@link #prepareJump}
     */
    public void merge(Map<String, Assignment> mapAddAsn, Map<String, Argument> mapAddArg)
        {
        Map<String, Assignment> mapAsn     = ensureDefiniteAssignments();
        boolean                 fCompletes = isReachable();

        for (Entry<String, Assignment> entry : mapAddAsn.entrySet())
            {
            String     sName  = entry.getKey();
            Assignment asnNew = entry.getValue();

            if (fCompletes)
                {
                asnNew = getVarAssignment(sName).join(asnNew);
                }
            mapAsn.put(sName, asnNew);

            Register regNew = (Register) mapAddArg.get(sName);
            if (regNew != null)
                {
                Register regOld = (Register) getVar(sName);
                assert regOld != null;

                TypeConstant typeOld = regOld.getType();
                TypeConstant typeNew = regNew.getType();
                if (!typeOld.equals(typeNew))
                    {
                    if (typeOld.isA(typeNew))
                        {
                        // the new type is wider - take it instead of the old narrower one
                        assert !regNew.isInPlace();
                        replaceArgument(sName, Branch.Always, regNew);
                        }
                    else
                        {
                        TypeConstant typeJoin = typeNew.union(pool(), typeOld);
                        if (!typeJoin.equals(typeOld))
                            {
                            replaceArgument(sName, Branch.Always, regOld.narrowType(typeJoin));
                            }
                        }
                    }
                }
            }
        }

    /**
     * Collect the narrowed arguments from the "else" branch of the assert expression and merge
     * them with the previously collected map of narrowed arguments.
     *
     * @param map (optional) map of previously collected arguments
     *
     * @return the merged map
     */
    protected Map<String, Argument> mergeNarrowedElseTypes(Map<String, Argument> map)
        {
        Map<String, Argument> mapThis = getNarrowingMap(false);
        if (map == null || map.isEmpty())
            {
            return mapThis.isEmpty() ? null : new HashMap<>(mapThis);
            }

        map.keySet().retainAll(mapThis.keySet()); // only keep common arguments
        for (Map.Entry<String, Argument> entry : map.entrySet())
            {
            String   sName   = entry.getKey();
            Argument argPrev = entry.getValue();
            Argument argThis = mapThis.get(sName);

            TypeConstant typePrev = argPrev.getType();
            TypeConstant typeThis = argThis.getType();

            TypeConstant typeJoin = typeThis.union(pool(), typePrev);
            if (!typeJoin.equals(typePrev))
                {
                if (argPrev instanceof Register regPrev)
                    {
                    map.put(sName, regPrev.narrowType(typeJoin));
                    }
                else
                    {
                    map.remove(sName);
                    }
                }
            }
        return map;
        }

    /**
     * Copy variable assignment information from this scope to the specified outer scope.
     *
     * @param ctxOuter  the context to copy the assignment information into
     */
    public void promoteAssignments(Context ctxOuter)
        {
        boolean fCompletes = isReachable();
        boolean fDemuxing  = isDemuxing();

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
                Assignment asnOuter = ctxOuter.getVarAssignment(sName);
                asnOuter = fCompletes
                        ? promote(sName, asnInner, asnOuter)
                        : asnOuter.promoteFromNonCompleting(asnInner);

                if (fDemuxing)
                    {
                    asnOuter = asnOuter.demux();
                    }
                ctxOuter.setVarAssignment(sName, asnOuter);
                }
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
     * Promote narrowing type information from this context to its enclosing context.
     */
    protected void promoteNarrowedTypes()
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
     * Promote narrowing generic type information from this context to its enclosing context.
     */
    protected void promoteNarrowedGenericTypes()
        {
        for (Entry<FormalConstant, TypeConstant> entry : getFormalTypeMap(Branch.Always).entrySet())
            {
            promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.Always);
            }
        for (Entry<FormalConstant, TypeConstant> entry : getFormalTypeMap(Branch.WhenTrue).entrySet())
            {
            promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.WhenTrue);
            }
        for (Entry<FormalConstant, TypeConstant> entry : getFormalTypeMap(Branch.WhenFalse).entrySet())
            {
            promoteNarrowedGenericType(entry.getKey(), entry.getValue(), Branch.WhenFalse);
            }
        }

    /**
     * Promote narrowing type information for the specified generic type from this context to its
     * enclosing context.
     *
     * @param constFormal  the generic type name
     * @param typeNarrow   the corresponding narrowed type
     * @param branch       the branch this narrowing comes from
     */
    protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrow,
                                              Branch branch)
        {
        if (branch == Branch.Always)
            {
            getOuterContext().replaceGenericType(constFormal, branch, typeNarrow);
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
     * Retain all the keys from the specified map at the specified branch.
     *
     * @param map        the map to use
     * @param fWhenTrue  the branch indicator
     */
    protected void retainNarrowedTypes(Map<String, Argument> map, boolean fWhenTrue)
        {
        Map<String, Argument> mapNarrowing = ensureNarrowingMap(fWhenTrue);
        if (!mapNarrowing.isEmpty())
            {
            if (map.isEmpty())
                {
                mapNarrowing.clear();
                }
            else
                {
                mapNarrowing.keySet().retainAll(map.keySet());
                }
            }
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

    public void unregisterVar(Token tokName)
        {
        String sName = tokName.getValueText();
        ensureNameMap().remove(sName);
        ensureDefiniteAssignments().remove(sName);
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
                arg = ctxOuter.getVar(sName, name, getOuterBranch(branch), errs);
                }
            }

        // we need to call resolveRegisterType() even on registers that are local
        // since some formal types could have been narrowed afterwards
        if (arg instanceof Register reg)
            {
            arg = resolveRegisterType(branch, reg);
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
        if (branch != Branch.Always)
            {
            Argument arg = getNarrowingMap(branch == Branch.WhenTrue).get(sName);
            if (arg != null)
                {
                return arg;
                }
            }

        return getNameMap().get(sName);
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
        return arg instanceof Register reg && reg.isInPlace();
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
                return arg instanceof Register reg && reg.isAllowedUnassigned();
                }

            return !(arg instanceof Register reg) || reg.isReadable();
            }

        // the only other readable variable names are reserved variables, and we need to ask
        // the containing context whether those are readable
        return isReservedName(sName) && getOuterContext().isVarReadable(sName);
        }

    /**
     * Mark the specified variable as being read from within this context.
     *
     * @param tokName  the variable name as a token from the source code
     * @param fDeref   true if the variable is dereferenced (e.g.: val); false if dereference is
     *                 suppressed (e.g: &val)
     * @param errs     the error list to log to
     */
    public final void markVarRead(Token tokName, boolean fDeref, ErrorListener errs)
        {
        markVarRead(false, tokName.getValueText(), tokName, fDeref, errs);
        }

    /**
     * Mark the specified variable as being read from within this context.
     * @param fNested  true if the variable is being read from within a context nested within
     *                 this context
     * @param sName    the variable name
     * @param tokName  the variable name as a token from the source code (optional)
     * @param fDeref   true if the variable is dereferenced (e.g.: val); false if dereference is
     *                 suppressed (e.g: &val)
     * @param errs     the error list to log to (optional)
     */
    protected void markVarRead(boolean fNested, String sName, Token tokName, boolean fDeref,
                               ErrorListener errs)
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
                    ctxOuter.markVarRead(true, sName, tokName, fDeref, errs);
                    }
                }
            }
        else
            {
            if (tokName != null && errs != null)
                {
                if (isReservedName(sName))
                    {
                    MethodStructure  method = getMethod();
                    IdentityConstant idCtx  = method == null
                            ? getThisClassId()
                            : method.getIdentityConstant();

                    tokName.log(errs, getSource(), Severity.ERROR,
                            sName.startsWith("this") ? Compiler.NO_THIS     :
                            sName.equals("super")    ? Compiler.NO_SUPER    :
                                                       Compiler.NAME_MISSING,
                            sName, idCtx.getValueString());

                    // add the variable to the reserved names that are allowed, to avoid
                    // repeating the same error logging
                    setVarAssignment(sName, Assignment.AssignedOnce);
                    }
                else if (fDeref) // unassigned non-dereferenced vars are allowed to be read

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
            if (arg instanceof Register reg && reg.isWritable())
                {
                return !reg.isMarkedFinal() ||
                        getVarAssignment(sName).isDefinitelyUnassigned();
                }
            return false;
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
     * @param tokName  the variable name as a token from the source code
     * @param fCond    true if the variable is conditionally assigned
     * @param errs     the error list to log to (optional)
     */
    public final void markVarWrite(Token tokName, boolean fCond, ErrorListener errs)
        {
        markVarWrite(tokName.getValueText(), tokName, fCond, errs);
        }

    /**
     * Mark an AstNode at the specified position as reliant on "this".
     *
     * @param lPos  the node's position
     * @param errs  the error list to log to (optional)
     */
    public void requireThis(long lPos, ErrorListener errs)
        {
        Context ctxOuter = getOuterContext();
        if (ctxOuter == null)
            {
            errs.log(Severity.ERROR, Compiler.NO_THIS, new Object[0], getSource(), lPos, lPos);
            }
        else
            {
            ctxOuter.requireThis(lPos, errs);
            }
        }

    /**
     * Mark the specified variable as being written to within this context.
     *
     * @param sName    the variable name
     * @param tokName  the variable name as a token from the source code (optional)
     * @param fCond    true if the variable is conditionally assigned
     * @param errs     the error list to log to (optional)
     */
    protected void markVarWrite(String sName, Token tokName, boolean fCond, ErrorListener errs)
        {
        if (getVar(sName) == null)
            {
            // this method can be called for variable names that don't exist only if there are
            // already compilation errors
            assert errs.hasSeriousErrors();
            return;
            }

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
     * Mark the specified formal type as being used within this context.
     *
     * @param type  the formal type or a type that contains formal types
     * @param errs  the error list to log to (optional)
     */
    public void useFormalType(TypeConstant type, ErrorListener errs)
        {
        Context ctxOuter = getOuterContext();
        if (ctxOuter != null)
            {
            ctxOuter.useFormalType(type, errs);
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
                ? Collections.emptyMap()
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
        return resolveName(sName, null, ErrorListener.BLACKHOLE);
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
                        ? Collections.emptyMap()
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
        return switch (sName)
            {
            case "this",
                 "this:target",
                 "this:public",
                 "this:protected",
                 "this:private",
                 "this:struct",
                 "this:class",
                 "this:service",
                 "this:module",
                 "super"
                -> true;

            default
                -> false;
            };
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
        // a formal type could be narrowed to its constraint, but is not assignable from it
        assert typeNarrow.isA(reg.getType()) || typeNarrow.isA(reg.getOriginalType()) ||
               reg.getOriginalType().isFormalType() ||
               reg.getOriginalType().getParamType(0).isFormalType();

        replaceArgument(sName, branch, reg.narrowType(typeNarrow));
        }

    /**
     * Replace the existing argument with the specified one for the given branch.
     */
    protected void replaceArgument(String sName, Branch branch, Argument argNew)
        {
        if (branch == Branch.Always)
            {
            if (argNew instanceof Register reg && isVarDeclaredInThisScope(sName))
                {
                // the narrowing register is replacing a local register; remember that fact
                reg.markInPlace();
                }
            ensureNameMap().put(sName, argNew);
            }
        else
            {
            ensureNarrowingMap(branch == Branch.WhenTrue).put(sName, argNew);
            }
        }

    /**
     * Replace the existing arguments with the specified one for the "Always" branch.
     */
    protected void replaceArguments(Map<String, Argument> mapArgs)
        {
        if (mapArgs != null)
            {
            for (Map.Entry<String, Argument> entry : mapArgs.entrySet())
                {
                replaceArgument(entry.getKey(), Context.Branch.Always, entry.getValue());
                }
            }
        }

    /**
     * Restore the existing argument using the saved off original register.
     *
     * @param sName    the argument name
     * @param regOrig  the original register
     */
    public void restoreArgument(String sName, Register regOrig)
        {
        Map<String, Argument> map = ensureNameMap();
        if (isVarDeclaredInThisScope(sName))
            {
            map.put(sName, regOrig);
            }
        else
            {
            map.remove(sName);
            }
        }

    /**
     * Replace (narrow) the generic type of the specified name in this context with the specified
     * target's type.
     */
    protected void replaceGenericArgument(FormalConstant constFormal, Branch branch, TargetInfo infoNew)
        {
        // place the info with the narrowed generic type (used by NamedTypeExpression)
        switch (branch)
            {
            case WhenTrue:
                if (constFormal instanceof PropertyConstant)
                    {
                    ensureNarrowingMap(true).put(constFormal.getName(), infoNew);
                    }
                break;

            case WhenFalse:
                if (constFormal instanceof PropertyConstant)
                    {
                    ensureNarrowingMap(false).put(constFormal.getName(), infoNew);
                    }
                break;

            default:
                if (constFormal instanceof PropertyConstant)
                    {
                    ensureNameMap().put(constFormal.getName(), infoNew);
                    }
                break;
            }

        replaceGenericType(constFormal, branch, infoNew.getType());
        }

    /**
     * Replace (narrow) the generic type of the specified name in this context.
     */
    protected void replaceGenericType(FormalConstant constFormal, Branch branch, TypeConstant typeNew)
        {
        assert typeNew.isTypeOfType();

        ensureFormalTypeMap(branch).put(constFormal, typeNew);
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
            TypeConstant typeOld = argOld.getType();
            TypeConstant typeNew = argNew.getType();

            TypeConstant typeJoin = typeNew.union(pool(), typeOld);
            if (!typeJoin.equals(typeOld))
                {
                if (argOld instanceof Register regOld)
                    {
                    map.put(sName, regOld.narrowType(typeJoin));
                    }
                else
                    {
                    map.remove(sName);
                    }
                }
            }
        }

    /**
     * Merge the types of the existing argument with the specified one for the given branch.
     */
    protected void joinGenericType(FormalConstant constFormal, Branch branch, Argument argNew)
        {
        Map<FormalConstant, TypeConstant> map = ensureFormalTypeMap(branch);

        TypeConstant typeOld = map.get(constFormal);
        if (typeOld != null)
            {
            TypeConstant typeNew = argNew.getType();

            TypeConstant typeJoin = typeNew.union(pool(), typeOld);
            map.put(constFormal, typeJoin);
            }
        else
            {
            map.remove(constFormal);
            }
        }

    /**
     * @return the read-only map that provides a name-to-narrowed type lookup
     */
    protected Map<String, Argument> getNarrowingMap(boolean fWhenTrue)
        {
        Map<String, Argument> map = fWhenTrue ? m_mapWhenTrue : m_mapWhenFalse;
        return map == null ? Collections.emptyMap() : map;
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
     * @return the read-only map that provides a formal constant-to-narrowed generic type lookup
     */
    protected Map<FormalConstant, TypeConstant> getFormalTypeMap(Branch branch)
        {
        Map<FormalConstant, TypeConstant> map = switch (branch)
            {
            case WhenTrue  -> m_mapFormalWhenTrue;
            case WhenFalse -> m_mapFormalWhenFalse;
            default        -> m_mapFormal;
            };
        return map == null ? Collections.emptyMap() : map;
        }

    /**
     * @return the map that provides a formal constant-to-narrowed generic type lookup
     */
    protected Map<FormalConstant, TypeConstant> ensureFormalTypeMap(Branch branch)
        {
        Map<FormalConstant, TypeConstant> map = getFormalTypeMap(branch);

        if (map.isEmpty())
            {
            return switch (branch)
                {
                case WhenTrue  -> m_mapFormalWhenTrue  = new HashMap<>();
                case WhenFalse -> m_mapFormalWhenFalse = new HashMap<>();
                default        -> m_mapFormal          = new HashMap<>();
                };
            }
        return map;
        }

    /**
     * @return a generic type resolver for a given branch; null if no narrowing info exists
     */
    protected GenericTypeResolver getLocalResolver(Branch branch)
        {
        return new GenericTypeResolver()
            {
            @Override
            public TypeConstant resolveFormalType(FormalConstant constFormal)
                {
                TypeConstant typeType = getFormalTypeMap(branch).get(constFormal);
                if (typeType == null)
                    {
                    return resolveLocalVar(constFormal.getName(), branch);
                    }
                assert typeType.isTypeOfType();
                return typeType.getParamType(0);
                }

            @Override
            public TypeConstant resolveGenericType(String sFormalName)
                {
                // in the absence of any additional information, only pick generic types
                for (Map.Entry<FormalConstant, TypeConstant> entry :
                            getFormalTypeMap(branch).entrySet())
                    {
                    FormalConstant constFormal = entry.getKey();
                    if (constFormal instanceof PropertyConstant &&
                            constFormal.getName().equals(sFormalName))
                        {
                        return entry.getValue();
                        }
                    }

                return resolveLocalVar(sFormalName, branch);
                }

            private TypeConstant resolveLocalVar(String sFormalName, Branch branch)
                {
                Argument arg = getLocalVar(sFormalName, branch);
                if (arg == null)
                    {
                    return isFunction()
                            ? null
                            : getThisClass().getFormalType().resolveGenericType(sFormalName);
                    }

                TypeConstant typeType = arg.getType();
                return typeType.isTypeOfType()
                        ? typeType.getParamType(0)
                        : null;
                }
            };
        }


    /**
     * Resolve the specified register's type on the specified branch.
     *
     * @return a potentially narrowed register
     */
    protected Argument resolveRegisterType(Branch branch, Register reg)
        {
        TypeConstant typeOriginal = reg.getType();
        TypeConstant typeResolved = typeOriginal.resolveGenerics(pool(), getLocalResolver(branch));

        return typeResolved.equals(typeOriginal)
                ? reg
                : reg.narrowType(typeResolved);
        }

    /**
     * Resolve the specified formal type within this context if possible.
     *
     * @param typeFormal  the formal type to resolve
     *
     * @return the resolved formal type if possible, or the passed in type
     */
    public TypeConstant resolveFormalType(TypeConstant typeFormal)
        {
        return resolveFormalType(typeFormal, Branch.Always);
        }

    /**
     * Resolve the specified formal type within this context on the specified branch.
     *
     * @param typeFormal  the formal type to resolve
     * @param branch      the branch to use
     *
     * @return the resolved formal type if possible, or the passed in type
     */
    protected TypeConstant resolveFormalType(TypeConstant typeFormal, Branch branch)
        {
        TypeConstant typeResolved = typeFormal.resolveGenerics(pool(), getLocalResolver(branch));

        if (typeResolved.containsFormalType(true))
             {
             // the type could be partially resolved; take it and keep going
             typeFormal = typeResolved;
             }
        else
             {
             return typeResolved;
             }

        Context ctxOuter = getOuterContext();
        return ctxOuter == null
                ? typeFormal
                : ctxOuter.resolveFormalType(typeFormal, getOuterBranch(branch));
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
        int              cSteps    = 0;
        IdentityConstant idParent  = clzParent.getIdentityConstant();
        MethodStructure  method    = getMethod();
        Component        component = method == null
                ? getThisClass()
                : method.getParent().getParent();

        while (component != null)
            {
            IdentityConstant id = component.getIdentityConstant();

            switch (id.getFormat())
                {
                case Module:
                case Package:
                case Class:
                    {
                    if (id.equals(idParent))
                        {
                        return cSteps;
                        }

                    ClassStructure clz = (ClassStructure) component;
                    if (clz.hasContribution(idParent))
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

                default:
                    throw new IllegalStateException();
                }

            component = component.getParent();
            }

        return -1;
        }

    /**
     * Produce a regular (not on stack) register.
     *
     * @param type   the type of the register
     * @param sName  the name of the register, or null for an anonymous (e.g. temp) register
     */
    public Register createRegister(TypeConstant type, String sName)
        {
        return new Register(type, sName, getMethod());
        }

    /**
     * @return a Register for the parameter at the specified index
     */
    public Register getParameter(int iReg)
        {
        Parameter param = getMethod().getParam(iReg);
        String    sName = param.getName();
        Register  reg   = (Register) getVar(sName);
        if (reg == null)
            {
            ensureNameMap().put(sName, reg = new Register(param.getType(), sName, iReg));
            }
        return reg;
        }

    /**
     * @return an array of RegisterAst<Constant> for the method parameters
     */
    public RegisterAST[] collectParameters()
        {
        // create registers for the method parameters
        MethodStructure method  = getMethod();
        int             cParams = method.getParamCount();
        if (cParams == 0)
            {
            return BinaryAST.NO_REGS;
            }

        RegisterAST[] aReg = new RegisterAST[cParams];
        for (int i = 0; i < cParams; i++)
            {
            Parameter param = method.getParam(i);

            Register reg = (Register) getVar(param.getName());
            aReg[i] = reg == null
                    ? new RegisterAST(param.getType(), param.getNameConstant())
                    : (RegisterAST) reg.getOriginalRegister().getRegisterAST();
            }
        return aReg;
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


    // ----- inner class: IfContext ----------------------------------------------------------------

    /**
     * A custom context implementation to provide type-narrowing as a natural side effect of an
     * "if" or a "ternary" block with a terminating branch.
     */
    static class IfContext
            extends Context
        {
        /**
         * Construct an IfContext.
         *
         * @param outer  the outer context
         */
        public IfContext(Context outer)
            {
            super(outer, true);
            }

        @Override
        public boolean isReachable()
            {
            // IfContext is unreachable if both branches exist and not reachable
            return m_fReachable &&
                    (m_ctxWhenTrue  == null || m_ctxWhenTrue .isReachable() ||
                     m_ctxWhenFalse == null || m_ctxWhenFalse.isReachable());
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
        protected void markVarWrite(String sName, Token tokName, boolean fCond, ErrorListener errs)
            {
            if (getVar(sName) != null && isVarWritable(sName))
                {
                Assignment asn = getVarAssignment(sName);
                if (fCond)
                    {
                    Assignment asnTrue = asn.whenTrue().applyAssignment();
                    asn = asnTrue.join(asn, false);
                    }
                else
                    {
                    asn = asn.applyAssignment();
                    }
                setVarAssignment(sName, asn);
                }
            else
                {
                super.markVarWrite(sName, tokName, fCond, errs);
                }
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            // choose the wider type of the two branches and promote to "Always"
            Context  ctxOuter = getOuterContext();
            Argument argOrig  = ctxOuter.getVar(sName);
            if (argOrig != null)
                {
                Argument     argTrue;
                Argument     argFalse;
                TypeConstant typeTrue;
                TypeConstant typeFalse;

                switch (branch)
                    {
                    case WhenTrue:
                        argTrue   = arg;
                        argFalse  = getNarrowingMap(false).get(sName);
                        typeTrue  = argTrue.getType();
                        typeFalse = argFalse == null ? argOrig.getType() : argFalse.getType();
                        break;

                    case WhenFalse:
                        argTrue = getNarrowingMap(true).get(sName);
                        if (argTrue != null)
                            {
                            // the argument has already been processed on the "true" branch
                            return;
                            }
                        argFalse  = arg;
                        typeTrue  = argOrig.getType();
                        typeFalse = argFalse.getType();
                        break;

                    default:
                        // already processed by the super call
                        return;
                    }

                if (typeFalse.isA(typeTrue))
                    {
                    if (argTrue != null)
                        {
                        ctxOuter.replaceArgument(sName, Branch.Always, argTrue);
                        }
                    }
                else if (typeTrue.isA(typeFalse))
                    {
                    if (argFalse != null)
                        {
                        ctxOuter.replaceArgument(sName, Branch.Always, argFalse);
                        }
                    }
                else if (argOrig instanceof Register regOrig)
                    {
                    // we may need to restore the original type
                    TypeConstant typeOrig = regOrig.getType();

                    if (!typeFalse.isA(typeOrig) || !typeTrue.isA(typeOrig))
                        {
                        ctxOuter.replaceArgument(sName, Branch.Always, regOrig.restoreType());
                        }
                    }
                }
            }

        @Override
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            // choose the wider type of the two branches and promote to "Always"
            if (branch == Branch.WhenTrue)
                {
                TypeConstant typeTrue  = typeNarrowed;
                TypeConstant typeFalse = getFormalTypeMap(Branch.WhenFalse).get(constFormal);
                if (typeFalse != null)
                    {
                    Context      ctxOuter = getOuterContext();
                    TypeConstant typeOrig = ctxOuter.getFormalTypeMap(Branch.Always).get(constFormal);
                    if (typeFalse.isA(typeTrue))
                        {
                        if (!typeTrue.equals(typeOrig))
                            {
                            ctxOuter.replaceGenericType(constFormal, Branch.Always, typeTrue);
                            }
                        }
                    else if (typeTrue.isA(typeFalse))
                        {
                        if (!typeFalse.equals(typeOrig))
                            {
                            ctxOuter.replaceGenericType(constFormal, Branch.Always, typeFalse);
                            }
                        }
                    }
                }
            }

        @Override
        protected void promoteNonCompleting(Context ctxInner)
            {
            // defer the reachability decision until the exit()
            }

        @Override
        public Context exit()
            {
            if (isReachable())
                {
                if (m_ctxWhenTrue != null && !m_ctxWhenTrue.isReachable())
                    {
                    discardBranch(true);
                    }
                else if (m_ctxWhenFalse != null && !m_ctxWhenFalse.isReachable())
                    {
                    discardBranch(false);
                    }
                }

            return super.exit();
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
            mapBranch = getFormalTypeMap(Branch.of(fWhenTrue));
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

            mapBranch = getFormalTypeMap(Branch.of(!fWhenTrue));
            if (!mapBranch.isEmpty())
                {
                ensureFormalTypeMap(Branch.of(fWhenTrue)).putAll(mapBranch);
                }

            // promote the other branch's assignments
            Context ctxPromote = fWhenTrue ? m_ctxWhenFalse : m_ctxWhenTrue;
            if (ctxPromote == null)
                {
                Map<String, Assignment> mapAssign = ensureDefiniteAssignments();
                for (Map.Entry<String, Assignment> entry : mapAssign.entrySet())
                    {
                    if (!isVarDeclaredInThisScope(entry.getKey()))
                        {
                        Assignment asnCurrent = entry.getValue();
                        entry.setValue(fWhenTrue ? asnCurrent.whenFalse() : asnCurrent.whenTrue());
                        }
                    }
                }
            else
                {
                Set<String> setVars = new HashSet<>();
                ctxPromote.collectVariables(setVars);

                Map<String, Assignment> mapAssign = ensureDefiniteAssignments();
                for (String sName : setVars)
                    {
                    if (!ctxPromote.isVarDeclaredInThisScope(sName))
                        {
                        Assignment asnPromote = ctxPromote.getVarAssignment(sName);
                        Assignment asnCurrent = mapAssign.get(sName);

                        assert asnPromote != null;

                        if (asnCurrent == null)
                            {
                            asnCurrent = asnPromote;
                            }
                        else
                            {
                            asnCurrent = fWhenTrue ? asnCurrent.whenFalse() : asnCurrent.whenTrue();
                            asnCurrent = asnCurrent.join(asnPromote);
                            }

                        if (!asnCurrent.equals(getVarAssignment(sName)))
                            {
                            mapAssign.put(sName, asnCurrent);
                            }
                        }
                    }
                }
            }

        @Override
        protected void unlink(Context ctxDiscarded)
            {
            if (ctxDiscarded == m_ctxWhenTrue)
                {
                m_ctxWhenTrue = null;
                }
            if (ctxDiscarded == m_ctxWhenFalse)
                {
                m_ctxWhenFalse = null;
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

        void markExclusive()
            {
            assert m_fWhenTrue; // exclusive is only allowed on a "true" branch
            m_fExclusive = true;
            }

        @Override
        protected Branch getOuterBranch(Branch branch)
            {
            return Branch.of(m_fWhenTrue);
            }

        @Override
        public Context exit()
            {
            Context ctxOuter = super.exit();

            if (m_fExclusive)
                {
                // simulate an unreachable "false" branch
                Context ctxFalse = ctxOuter.enterFork(false);
                ctxFalse.setReachable(false);
                ctxFalse.exit();
                }
            return ctxOuter;
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
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            // promote our "always" into the corresponding parent's branch
            if (branch == Branch.Always)
                {
                getOuterContext().replaceGenericType(constFormal, Branch.of(m_fWhenTrue), typeNarrowed);
                }
            }

        private final boolean m_fWhenTrue;
        private       boolean m_fExclusive;
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
        protected Branch getOuterBranch(Branch branch)
            {
            // we only use the parent's "true" branch
            return Branch.WhenTrue;
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
            // the "when true" portion of this context replaces the "when true" portion of the
            // outer context;  the "when false" portion of this context is combined with the
            // "when false" portion of the outer context
            Assignment asnFalse = Assignment.join(asnOuter.whenFalse(), asnInner.whenFalse());
            Assignment asnJoin  = Assignment.join(asnFalse, asnInner.whenTrue());
            return asnJoin;
            }

        @Override
        protected void promoteNarrowedTypes()
            {
            // retain only our "false" entries in the parent's "false" context;
            // consider an example:
            //    Int? a; Int? b;
            //    if (a != null &&  // IfContext : "a" is Int for "true"; null for "false"
            //        b != null)    // AndContext: "b" is Int for "true"; null for "false"
            //      {
            //                      // exit from AndContext infers "a" and "b" are Int into
            //      }               // the "true" branch of the parent IfContext
            //    else
            //      {
            //                      // exit from AndContext infers nothing into the "false" branch
            //      }               // of the parent IfContext
            //
            getOuterContext().retainNarrowedTypes(getNarrowingMap(false), false);

            super.promoteNarrowedTypes();
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
        protected void promoteNarrowedGenericTypes()
            {
            // retain only our "false" entries in the parent's "false" context
            Map<FormalConstant, TypeConstant> map = getFormalTypeMap(Branch.WhenFalse);
            if (!map.isEmpty())
                {
                getOuterContext().ensureFormalTypeMap(Branch.WhenFalse).keySet().
                    retainAll(map.keySet());
                }
            super.promoteNarrowedGenericTypes();
            }

        @Override
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            super.promoteNarrowedGenericType(constFormal, typeNarrowed, branch);

            // promote our "true" into the parent's "true" branch and
            // join our "false" with the parent's "false"
            switch (branch)
                {
                case WhenTrue:
                    getOuterContext().replaceGenericType(constFormal, branch, typeNarrowed);
                    break;

                case WhenFalse:
                    getOuterContext().joinGenericType(constFormal, branch, typeNarrowed);
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
        protected Branch getOuterBranch(Branch branch)
            {
            // we only use the parent's "false" branch
            return Branch.WhenFalse;
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
            // outer context;  the "when true" portion of this context is combined with the
            // "when true" portion of the outer context
            Assignment asnTrue = Assignment.join(asnOuter.whenTrue(), asnInner.whenTrue());
            Assignment asnJoin = Assignment.join(asnInner.whenFalse(), asnTrue);
            return asnJoin;
            }

        @Override
        protected void promoteNarrowedTypes()
            {
            // inversely to the AndContext, retain only our "true" entries in the parent's "true"
            // context
            getOuterContext().retainNarrowedTypes(getNarrowingMap(true), true);

            super.promoteNarrowedTypes();
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
        protected void promoteNarrowedGenericTypes()
            {
            // retain only our "true" entries in the parent's "true" context
            Map<FormalConstant, TypeConstant> map = getFormalTypeMap(Branch.WhenTrue);
            if (!map.isEmpty())
                {
                getOuterContext().ensureFormalTypeMap(Branch.WhenTrue).keySet().
                    retainAll(map.keySet());
                }
            super.promoteNarrowedGenericTypes();
            }

        @Override
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            super.promoteNarrowedGenericType(constFormal, typeNarrowed, branch);

            // promote our "false" into the parent's "false" branch and
            // join our "true" with the parent's "true"
            switch (branch)
                {
                case WhenFalse:
                    getOuterContext().replaceGenericType(constFormal, branch, typeNarrowed);
                    break;

                case WhenTrue:
                    getOuterContext().joinGenericType(constFormal, branch, typeNarrowed);
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
        protected Branch getOuterBranch(Branch branch)
            {
            return branch.complement();
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
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            if (branch == Branch.Always)
                {
                super.promoteNarrowedGenericType(constFormal, typeNarrowed, branch);
                }
            else
                {
                getOuterContext().replaceGenericType(constFormal, branch.complement(), typeNarrowed);
                }
            }
        }


    // ----- inner class: AndIfContext -------------------------------------------------------------

    /**
     * A custom context implementation to provide type-narrowing as a natural side effect of a
     * chain of "if" conditions. The innermost AndIfContext is used to compile the "then" block of
     * the multi-condition "if", while the outermost IfContext is used to compile the "else" block.
     * This context behaves as a combination of the AndContext and IfContext.
     */
    static class AndIfContext
            extends AndContext
        {
        /**
         * Construct an AndIfContext.
         *
         * @param outer  the outer context
         */
        public AndIfContext(Context outer)
            {
            super(outer);
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            // the "when true" portion of this context replaces the "when true" portion of the
            // outer context
            Assignment asnJoin = Assignment.join(asnOuter.whenFalse(), asnInner.whenTrue());
            return asnJoin;
            }

        @Override
        public Context exit()
            {
            if (!isReachable())
                {
                // "then" block is not reachable; reflect that fact at the outer IfContext's
                // "whenTrue" branch, which is never created directly (via ensureFork(true))
                Context ctxOuter = getOuterContext();
                while (true)
                    {
                    if (ctxOuter instanceof IfContext ctxIf)
                        {
                        assert ctxIf.m_ctxWhenTrue == null;
                        ctxIf.enterFork(true).setReachable(false);
                        setReachable(true);
                        break;
                        }
                    ctxOuter = ctxOuter.getOuterContext();
                    }
                }

            return super.exit();
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

            f_typeLeft = typeLeft;
            }

        @Override
        public Context enterList()
            {
            TypeConstant typeCtx = f_typeLeft;
            TypeConstant typeEl  = typeCtx.isA(pool().typeList())
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
            errs = errs.branch(null);

            Argument arg = super.resolveRegularName(ctxFrom, sName, name, errs);

            // TargetInfo carries a "local" view of the name and has a preference over the
            // inference; otherwise, the inference takes precedence
            if (!(arg instanceof TargetInfo))
                {
                SimpleCollector  collector = new SimpleCollector(errs);
                TypeConstant     typeLeft  = f_typeLeft;
                Access           access    = typeLeft.getAccess();
                MethodStructure  method    = getMethod();
                MethodConstant   idMethod  = method == null ? null : method.getIdentityConstant();

                if (typeLeft.resolveContributedName(sName, access, idMethod, collector) == ResolutionResult.RESOLVED)
                    {
                    // inference succeeded
                    return collector.getResolvedConstant();
                    }
                }

            errs.merge();
            return arg;
            }

        @Override
        public void registerVar(Token tokName, Register reg, ErrorListener errs)
            {
            getOuterContext().registerVar(tokName, reg, errs);
            }

        @Override
        public void unregisterVar(Token tokName)
            {
            getOuterContext().unregisterVar(tokName);
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            switch (branch)
                {
                case Always:
                    super.promoteNarrowedType(sName, arg, branch);
                    break;

                case WhenTrue:
                case WhenFalse:
                    // promote our "true" into the parent's "true" branch and
                    // our "false" int the parent's "false"
                    getOuterContext().replaceArgument(sName, branch, arg);
                    break;
                }
            }

        @Override
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            // promote all
            getOuterContext().replaceGenericType(constFormal, branch, typeNarrowed);
            }

        private final TypeConstant f_typeLeft;
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
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            ensureCaptureMap().put(sName, true);
            return asnOuter.applyAssignmentFromCapture();
            }

        @Override
        public void requireThis(long lPos, ErrorListener errs)
            {
            captureThis();
            }

        @Override
        protected void markVarRead(boolean fNested, String sName, Token tokName, boolean fDeref, ErrorListener errs)
            {
            if (!isVarDeclaredInThisScope(sName) && getOuterContext().isVarReadable(sName))
                {
                // capture the variable
                ensureCaptureMap().putIfAbsent(sName, false);
                }

            super.markVarRead(fNested, sName, tokName, fDeref, errs);
            }

        @Override
        public void useFormalType(TypeConstant type, ErrorListener errs)
            {
            super.useFormalType(type, errs);

            if (type.isFormalType())
                {
                FormalConstant constFormal = (FormalConstant) type.getDefiningConstant();
                switch (constFormal.getFormat())
                    {
                    case Property:
                    case TypeParameter:
                        {
                        String   sName = constFormal.getName();
                        Argument arg   = resolveName(sName, null, errs);
                        assert arg != null;
                        ensureFormalMap().putIfAbsent(sName, arg);
                        break;
                        }

                    case FormalTypeChild:
                        {
                        FormalConstant constParent = (FormalConstant) constFormal.getParentConstant();
                        useFormalType(constParent.getType(), errs);
                        break;
                        }

                    case DynamicFormal:
                        break;
                    }
                }
            else if (type.containsFormalType(true))
                {
                Set<TypeConstant> setTypes = new HashSet<>();
                type.collectFormalTypes(true, setTypes);

                for (TypeConstant typeFormal : setTypes)
                    {
                    useFormalType(typeFormal, errs);
                    }
                }
            }

        @Override
        protected void collectVariables(Set<String> setVars)
            {
            // there's no visibility across LambdaContext or AnonymousInnerClassContext
            }

        /**
         * @return a map of variable name to a Boolean representing if the capture is read-only
         *         (false) or read/write (true)
         */
        public Map<String, Boolean> getCaptureMap()
            {
            return m_mapCapture == null
                    ? Collections.emptyMap()
                    : m_mapCapture;
            }

        /**
         * Obtain the map of names to the registers, if it has been built.
         * <p/>
         * Note: built by exit()
         *
         * @return a non-null map of the variable name to a Register for all variables to capture
         */
        public Map<String, Register> ensureRegisterMap()
            {
            Map<String, Register> map = m_mapRegisters;
            if (map == null)
                {
                if (getCaptureMap().isEmpty())
                    {
                    // there are never more capture-registers than there are captures
                    return Collections.emptyMap();
                    }

                m_mapRegisters = map = new HashMap<>();
                }

            return map;
            }

        /**
         * @return a map of names to generic type TargetInfo or type parameter's Register
         */
        public Map<String, Argument> getFormalMap()
            {
            return m_mapFormalInfo == null
                    ? Collections.emptyMap()
                    : m_mapFormalInfo;
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
         * @return a non-null map of variable name to generic type TargetInfo or
         *         type parameter Register
         */
        protected Map<String, Argument> ensureFormalMap()
            {
            Map<String, Argument> map = m_mapFormalInfo;
            if (map == null)
                {
                // use a tree map, to keep the captures in alphabetical order, which will
                // help to produce lambdas with a "predictable" signature
                m_mapFormalInfo = map = new TreeMap<>();
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
         * A map of formal types, collected by useFormalType(). Values are either of TargetInfo or
         * Register type.
         */
        private Map<String, Argument> m_mapFormalInfo;

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
            return switch (this)
                {
                case Always    -> Always;
                case WhenTrue  -> WhenFalse;
                case WhenFalse -> WhenTrue;
                };
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
    private final boolean m_fDemuxOnExit;

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
     * Each formal type narrowed within this context is registered in this map, along with the
     * narrowing TypeConstant.
     */
    private Map<FormalConstant, TypeConstant> m_mapFormal;

    /**
     * Each formal type declared within a parent context, may narrow its type in a child context
     * for a "true" branch.
     */
    private Map<FormalConstant, TypeConstant> m_mapFormalWhenTrue;

    /**
     * Each formal type declared within a parent context, may narrow its type in a child context
     * for a "false" branch.
     */
    private Map<FormalConstant, TypeConstant> m_mapFormalWhenFalse;

    /**
     * Each variable assigned within this context is registered in this map. The corresponding value
     * represents the assignments that may have occurred by this point.
     */
    private Map<String, Assignment> m_mapAssigned;

    /**
     * Cached "this register".
     */
    private Register m_regThis;
    }