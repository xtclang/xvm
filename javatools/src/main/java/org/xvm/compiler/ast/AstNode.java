package org.xvm.compiler.ast;


import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.stream.Collectors;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.ComponentResolver;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.NamedConstant;
import org.xvm.asm.constants.PendingTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Expression.TypeFit;
import org.xvm.compiler.ast.NameExpression.Meaning;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.dup;
import static org.xvm.util.Handy.indentLines;


/**
 * Common base class for all statements and expressions.
 */
public abstract class AstNode
        implements Cloneable
    {
    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Obtain the AstNode that contains this node. A parent is configured by the scan phase.
     *
     * @return  the parent node, or null
     */
    public AstNode getParent()
        {
        return m_parent;
        }

    /**
     * Specify a parent for the AstNode.
     *
     * @param parent  the parent node
     */
    protected void setParent(AstNode parent)
        {
        assert parent == null || !this.isDiscarded() && !parent.isDiscarded();
        this.m_parent = parent;
        }

    /**
     * This method recurses through the tree of AstNode objects, allowing each node to introduce
     * itself as the parent of each node under it.
     */
    protected void introduceParentage()
        {
        for (AstNode node : children())
            {
            node.setParent(this);
            node.introduceParentage();
            }
        }

    /**
     * Helper: Given an optional Iterable of child AstNode objects, set the parent of each of the
     * children to be this node.
     *
     * @param children  an Iterable of AstNode, or null
     */
    protected void adopt(Iterable<? extends AstNode> children)
        {
        if (children != null)
            {
            for (AstNode child : children)
                {
                child.setParent(this);
                }
            }
        }

    /**
     * Helper: Given an optional AstNode object, set its parent to this node.
     *
     * @param child  an AstNode, or null
     *
     * @return the same child as passed, which may be null
     */
    protected <T extends AstNode> T adopt(T child)
        {
        if (child != null)
            {
            child.setParent(this);
            }

        return child;
        }

    /**
     * Return an Iterable/Iterator that represents all the child nodes of this node.
     *
     * @return an Iterable of child nodes (from whence an Iterator can be obtained)
     */
    public ChildIterator children()
        {
        Field[] fields = getChildFields();
        return fields.length == 0 ? ChildIterator.EMPTY : new ChildIteratorImpl(fields);
        }

    /**
     * Replace the specified child of this AstNode with a new child.
     *
     * @param nodeOld  the child to replace
     * @param nodeNew  the new child
     */
    public void replaceChild(AstNode nodeOld, AstNode nodeNew)
        {
        ChildIterator children = children();
        for (AstNode node : children)
            {
            if (node == nodeOld)
                {
                children.replaceWith(adopt(nodeNew));
                return;
                }
            }
        throw new IllegalStateException("no such child \"" + nodeOld + "\" on \"" + this + '\"');
        }

    /**
     * For a node contained somewhere in the AST tree under this node, find the immediate child of
     * this node that is or contains the specified node.
     *
     * @param node  the node that is a descendant of this node
     *
     * @return the child that is an immediate child of this node under which the node occurs, or
     *         null
     */
    public AstNode findChild(AstNode node)
        {
        AstNode child  = node;
        do
            {
            AstNode parent = child.getParent();
            if (parent == this)
                {
                return child;
                }

            child = parent;
            }
        while (child != null);

        return null;
        }

    /**
     * Mark the node as being discarded.
     *
     * @param fRecurse  pass true to discard the entire tree from this node down
     */
    protected void discard(boolean fRecurse)
        {
        m_stage = Stage.Discarded;
        if (fRecurse)
            {
            for (AstNode node : children())
                {
                node.discard(true);
                }
            }
        }

    /**
     * @return true iff the node has been discarded
     */
    protected boolean isDiscarded()
        {
        return m_stage == Stage.Discarded;
        }

    /**
     * @return an array of fields on this AstNode that contain references to child AstNodes
     */
    protected Field[] getChildFields()
        {
        return NO_FIELDS;
        }

    @Override
    public AstNode clone()
        {
        AstNode that;
        try
            {
            that = (AstNode) super.clone();
            }
        catch (CloneNotSupportedException e)
            {
            throw new IllegalStateException(e);
            }

        for (Field field : getChildFields())
            {
            Object oVal;
            try
                {
                oVal = field.get(this);
                }
            catch (NullPointerException e)
                {
                throw new IllegalStateException("class=" + this.getClass().getSimpleName(), e);
                }
            catch (IllegalAccessException e)
                {
                throw new IllegalStateException(e);
                }

            if (oVal != null)
                {
                if (oVal instanceof AstNode node)
                    {
                    AstNode nodeNew = node.clone();

                    that.adopt(nodeNew);
                    oVal = nodeNew;
                    }
                else if (oVal instanceof List list)
                    {
                    ArrayList<AstNode> listNew = new ArrayList<>();
                    for (AstNode node : (List<AstNode>) list)
                        {
                        listNew.add(node.clone());
                        }

                    that.adopt(listNew);
                    oVal = listNew;
                    }
                else
                    {
                    throw new IllegalStateException(
                            "unsupported container type: " + oVal.getClass().getSimpleName());
                    }

                try
                    {
                    field.set(that, oVal);
                    }
                catch (IllegalAccessException e)
                    {
                    throw new IllegalStateException(e);
                    }
                }
            }

        return that;
        }

    /**
     * @return the current compilation stage for this node
     */
    public Stage getStage()
        {
        return m_stage;
        }

    /**
     * Test if the node has reached the specified stage.
     *
     * @param stage  the compilation stage to test for
     *
     * @return true if the node has already reached or passed the specified stage
     */
    protected boolean alreadyReached(Stage stage)
        {
        assert stage != null;
        return getStage().compareTo(stage) >= 0;
        }

    /**
     * Update the stage to the specified stage, if the specified stage is later than the current
     * stage.
     *
     * @param stage  the suggested stage
     */
    protected void setStage(Stage stage)
        {
        // stage is a "one way" attribute
        if (stage != null && stage.compareTo(m_stage) > 0)
            {
            m_stage = stage;
            }
        }

    /**
     * Obtain the Source for this AstNode, if any. By default, a node uses the same source as its
     * parent.
     *
     * @return a Source instance
     */
    public Source getSource()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getSource();
        }

    /**
     * Determine the starting position in the source at which this AstNode occurs.
     *
     * @return the Source position of the AstNode
     */
    public abstract long getStartPosition();

    /**
     * Determine the ending position (exclusive) in the source for this AstNode.
     *
     * @return the Source position of the end of the AstNode
     */
    public abstract long getEndPosition();

    /**
     * Populate the MethodStructure with the source code from this AST node.
     *
     * @param method  the MethodStructure to donate the source code to
     */
    void donateSource(MethodStructure method)
        {
        if (method != null)
            {
            long   lStart = getStartPosition();
            long   lEnd   = getEndPosition();
            String sSrc   = getSource().toString(lStart, lEnd);
            int    iLine  = Source.calculateLine(lStart);
            int    of     = Source.calculateOffset(lStart);
            if (of > 0)
                {
                sSrc = dup(' ', of) + sSrc;
                }
            method.configureSource(sSrc, iLine);
            }
        }

    /**
     * @return true iff this node holds a component
     */
    public boolean isComponentNode()
        {
        return false;
        }

    /**
     * Obtain the Component for this AstNode, if any.
     *
     * @return the Component containing this AstNode
     */
    public Component getComponent()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getComponent();
        }

    /**
     * Obtain the ComponentResolver for this AstNode, if any.
     *
     * @return the ComponentResolver for this AstNode
     */
    public ComponentResolver getComponentResolver()
        {
        return getComponent();
        }

    /**
     * @return the "compilation container" for a statement or expression, which is the method
     *         ({@link MethodDeclarationStatement}), anonymous inner class ({@link NewExpression}),
     *         lambda function ({@link LambdaExpression}), or "inlined" lambda
     *         ({@link StatementExpression})
     */
    protected AstNode getCodeContainer()
        {
        AstNode parent = getParent();
        return parent == null
                ? null
                : parent.getCodeContainer();
        }

    /**
     * @return the closest StatementBlock parent
     */
    public StatementBlock getParentBlock()
        {
        AstNode parent = getParent();
        while (parent != null)
            {
            if (parent instanceof StatementBlock block)
                {
                return block;
                }
            parent = parent.getParent();
            }
        return null;
        }

    /**
     * Given a type expression that is used as some part of this AstNode, determine if that type is
     * allowed to auto narrow.
     *
     * @param type  a TypeExpression that is a child of this AstNode
     *
     * @return true iff the specified TypeExpression is being used in a place that supports
     *         auto-narrowing
     */
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return true;
        }

    /**
     * Code Container method: TODO
     *
     * @return the required return types from the code container, which comes from the signature if
     *         is specified, or from the specified required type during validation, or from the
     *         actual type once the expression is validated
     */
    public TypeConstant[] getReturnTypes()
        {
        throw notCodeContainer();
        }

    /**
     * Code Container method: Determine if the code container represents a method or function with
     * a conditional return.
     *
     * @return true iff the code container has a conditional return
     */
    public boolean isReturnConditional()
        {
        throw notCodeContainer();
        }

    /**
     * Code Container method: TODO
     *
     * @param atypeRet  the types being returned
     */
    public void collectReturnTypes(TypeConstant[] atypeRet)
        {
        throw notCodeContainer();
        }

    protected RuntimeException notCodeContainer()
        {
        throw new IllegalStateException("not code container: " + this.getClass().getSimpleName());
        }

    /**
     * @return true iff this AstNode (or an AstNode that it contains) references "super"
     */
    protected boolean usesSuper()
        {
        return false;
        }

    /**
     * (LValue method)
     *
     * @return true iff this AstNode is syntactically capable of being an L-Value
     */
    public boolean isLValueSyntax()
        {
        return false;
        }

    /**
     * (LValue method)
     *
     * @return the syntactically-capable LValue expression, iff {@link #isLValueSyntax()} returns
     *         true
     */
    public Expression getLValueExpression()
        {
        throw notLValue();
        }

    /**
     * (LValue method)
     *
     * @param ctx     the compiler context
     * @param branch  the branch to apply the inference to
     * @param aTypes  the type of the RValue
     */
    public void updateLValueFromRValueTypes(Context ctx, Context.Branch branch, TypeConstant[] aTypes)
        {
        throw notLValue();
        }

    private RuntimeException notLValue()
        {
        assert !isLValueSyntax();
        throw new IllegalStateException("not LValue: " + this.getClass().getSimpleName());
        }

    /**
     * Test if the specified child is used as an R-Value, which is something that yields a value.
     * <p/>
     * In most cases, an expression is used as an R-Value (i.e. it has a value), but an expression
     * can be used as a left side of an assignment, for example, which makes it an L-Value. In a
     * few cases, an expression can be used as both an R-Value and an L-Value, such as with the
     * pre-/post-increment/-decrement operators.
     *
     * @param exprChild  an expression that is a child of this node
     *
     * @return true iff the child is used as an R-Value
     */
    protected boolean isRValue(Expression exprChild)
        {
        return true;
        }

    /**
     * Test if the specified child is allowed to produce a conditional result. For example, certain
     * types of assignment support conditional r-value expressions, and the "return" statement can
     * disallow a conditional r-value.
     *
     * @param exprChild  an expression that is a child of this node
     *
     * @return true iff the child is allowed to produce a conditional result
     */
    protected boolean allowsConditional(Expression exprChild)
        {
        return false;
        }

    /**
     * Test if the specified child is allowed to short-circuit.
     *
     * @param nodeChild  an AstNode (typically, an expression) that is a child of this node
     *
     * @return true iff the child is allowed to short-circuit
     */
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return false;
        }

    /**
     * This must be overridden by any AST node that supports short-circuiting children. This is
     * called during validation by a child that needs a ground.
     *
     * @param nodeOrigin  the node which is the origin of the short circuit
     * @param ctxOrigin   the validating context from the point of the short circuit
     *
     * @return the label to jump to when the expression short-circuits.
     */
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        throw new IllegalStateException("no short circuit label for: " + this.getClass().getSimpleName());
        }

    /**
     * Collect all the expressions that should be included in tracing debug output. This is used
     * by "assert", for example, to display information relevant to the assertion.
     *
     * @param mapExprs  the expressions collected thus far, keyed by their source code
     *                  representations
     */
    protected void selectTraceableExpressions(Map<String, Expression> mapExprs)
        {
        for (AstNode node : children())
            {
            if (node instanceof Expression expr)
                {
                if (expr.isValidated() && expr.isTraceworthy())
                    {
                    String sExpr = expr.toString();
                    if (!mapExprs.containsKey(sExpr))
                        {
                        mapExprs.put(sExpr, expr);
                        }
                    }

                if (expr.isConstant())
                    {
                    // don't recurse constants
                    continue;
                    }
                }

            node.selectTraceableExpressions(mapExprs);
            }
        }

    /**
     * (Post-validation) Determine if the statement or expression is able to complete normally.
     * <p/>
     * This method must be overridden by any statement or expression that may not complete, either
     * due to its own implementation or that of another AST node that is delegated to.
     *
     * @return true iff the AST node is able to complete
     */
    public boolean isCompletable()
        {
        return true;
        }

    /**
     * @return true iff the AST node is a T0D0 statement or expression
     */
    public boolean isTodo()
        {
        return false;
        }

    /**
     * @return the constant pool
     */
    protected ConstantPool pool()
        {
        AstNode nodeParent = getParent();
        return nodeParent == null
                ? null
                : nodeParent.pool();
        }

    /**
     * For nested nodes, determine the default access if the nodes need to specify an accessibility.
     *
     * @return the accessibility that this node should assume if this node has to specify its own
     *         accessibility and no accessibility is specified
     */
    public Access getDefaultAccess()
        {
        AstNode parent = getParent();
        return parent == null
                ? Access.PUBLIC
                : parent.getDefaultAccess();
        }

    /**
     * Helper to log an error related to this AstNode.
     *
     * @param errs        the ErrorListener to log to
     * @param severity    the severity level of the error; one of
     *                    {@link Severity#INFO}, {@link Severity#WARNING},
     *                    {@link Severity#ERROR}, or {@link Severity#FATAL}
     * @param sCode       the error code that identifies the error message
     * @param aoParam     the parameters for the error message; may be null
     *
     * @return true to attempt to abort the process that reported the error, or
     *         false to attempt to continue the process
     */
    public boolean log(ErrorListener errs, Severity severity, String sCode, Object... aoParam)
        {
        Source source = getSource();
        return errs == null
                ? severity.ordinal() >= Severity.ERROR.ordinal()
                : errs.log(severity, sCode, aoParam, source,
                source == null ? 0L : getStartPosition(),
                source == null ? 0L : getEndPosition());
        }


    // ----- compile phases ------------------------------------------------------------------------

    /**
     * First logical compiler pass.
     * <p/>
     * <ul>
     * <li>At this point, names are NOT resolvable; we're really just organizing the tree and
     * checking for errors that are obvious from "this point down" (no lateral evaluation of
     * structures, because we can't count on them even existing yet.)</li>
     *
     * <li>The general idea is that this method recurses through the structure, allowing each node
     * to introduce itself as the parent of each node under it, and that the nodes which will be
     * structures in the resulting FileStructure will register themselves.</li>
     *
     * <li>Type parameters for the types must also be registered, because they are also types, and
     * they will be required to already be present when the second pass begins.</li>
     * </ul>
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Second logical compiler pass. This pass has access to imported modules, and is responsible
     * for resolving names.
     * <p/>
     * The rule of thumb is that no questions should be asked of other modules that could not have
     * been answered by this module before this call; in other words, the order of the module
     * compilation is not only unpredictable, but the potential exists for dependencies in either
     * direction (first to last and/or vice versa).
     * <p/>
     * As a result, some questions may come to an AstNode to resolve that it is not yet prepared to
     * resolve, in which case the caller (another AstNode) has to add itself to the list of nodes
     * that require another pass.
     * <p/>
     * <ul>
     * <li>Packages that import modules are able to verify that those modules are available to
     * compile against;</li>
     *
     * <li>Conditionals must be resolvable, e.g. the link-time conditionals defining which types are
     * present and which of their Compositions are in effect.</li>
     * </ul>
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Third logical compiler pass. This pass is responsible for resolving types, constant values,
     * and structures within methods. To accomplish this, this pass must be able to resolve type
     * names, which is why the second pass was necessarily a separate pass.
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Fourth logical compiler pass. Emits the resulting, finished structures.
     *
     * @param mgr   the Stage Manager that is conducting the processing
     * @param errs  the error list to log any errors etc. to
     */
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        }

    /**
     * Helper to update the line number in the code to the line number on which this AstNode began.
     *
     * @param code  the Code being emitted
     */
    protected void updateLineNumber(Code code)
        {
        code.updateLineNumber(Source.calculateLine(getStartPosition()));
        }

    /**
     * If any of the children of this node have been previously deferred, catch them up now.
     *
     * @param errs  the error list to log to
     *
     * @return true if the children got caught up; false if the catch-up aborted
     */
    protected boolean catchUpChildren(ErrorListener errs)
        {
        // determine what stage we're trying to catch the children up to
        Stage stageTarget = getStage();
        if (!stageTarget.isTargetable())
            {
            // we want to catch up to the last target that this component completed, and not
            // actually pass the stage that this component is at, which can be accomplished using
            // StageMgr processChildrenExcept() or processChildren() if the children need to
            // complete the stage that this node is currently working on (if this node is currently
            // in a transition stage)
            stageTarget = stageTarget.prevTarget();
            if (!stageTarget.isTargetable())
                {
                assert stageTarget == Stage.Initial;
                return true;
                }
            }

        // method children are all deferred up until this stage, so we have to "catch them up" at
        // this point, recreating the various compiler stages here; start by collecting all the
        // children that may need to be processed and figuring out how far behind the oldest is
        Stage         stageOldest  = null;
        List<AstNode> listChildren = new ArrayList<>();
        for (AstNode node : children())
            {
            Stage stage = node.getStage();
            if (stage.compareTo(stageTarget) < 0)
                {
                listChildren.add(node);

                if (stageOldest == null)
                    {
                    stageOldest = stage;
                    }
                else if (stage.compareTo(stageOldest) < 0)
                    {
                    stageOldest = stage;
                    }
                }
            }
        if (stageOldest == null)
            {
            return true;
            }

        ErrorListener errsTemp = errs.branch(this);
        while (stageOldest.compareTo(stageTarget) < 0)
            {
            Stage    stageNext = stageOldest.nextTarget();
            StageMgr mgrKids   = new StageMgr(listChildren, stageNext, errsTemp);
            for (int cTries = 0; !mgrKids.processComplete(); cTries++)
                {
                if (errsTemp.isAbortDesired() || cTries > 20)
                    {
                    mgrKids.logDeferredAsErrors(errsTemp);
                    errsTemp.merge();
                    return false;
                    }
                }

            stageOldest = stageNext;
            }

        boolean fFailure = errsTemp.hasSeriousErrors();
        errsTemp.merge();
        return !fFailure;
        }

    // ----- name resolution -----------------------------------------------------------------------

    /**
     * Determine if this particular node has an import registered on it of the specified name.
     *
     * @param sName  the simple name
     * @param errs   the error listener
     *
     * @return an ImportStatement, or null
     */
    protected ImportStatement resolveImportBySingleName(String sName, ErrorListener errs)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveImportBySingleName(sName, errs);
        }

    /**
     * @return true iff this AstNode should be able to resolve names
     */
    protected boolean canResolveNames()
        {
        if (this instanceof NameResolver.NameResolving resolver)
            {
            // the problem is this: that a NameResolver that hasn't been invoked as part of the
            // natural pass of the resolveNames() recursion has not had a chance to figure out what
            // the effect its imports may have on name resolution, and thus we can't ask it what a
            // name means
            return !resolver.getNameResolver().isFirstTime();
            }

        // for all other components (that don't override this method because they know more about
        // whether or not they can resolve names), we'll assume that if they haven't been resolved,
        // then they don't know how to resolve names
        return alreadyReached(Stage.Resolving);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Validate the specified expressions against the required types.
     *
     * @param ctx            the compiler context
     * @param listExpr       the list of expressions (can be modified)
     * @param atypeRequired  the required types array
     * @param errs           the error listener
     *
     * @return an array of TypeConstants describing the actual expression types or null
     *         if the validation fails, in which case an error has been reported
     */
    protected TypeConstant[] validateExpressions(Context ctx, List<Expression> listExpr,
                                                 TypeConstant[] atypeRequired, ErrorListener errs)
        {
        int            cReq   = atypeRequired == null ? 0 : atypeRequired.length;
        int            cExprs = listExpr.size();
        TypeConstant[] atype  = new TypeConstant[cExprs];
        boolean        fValid = true;
        for (int i = 0; i < cExprs; ++i)
            {
            Expression exprOld = listExpr.get(i);
            if (exprOld.isValidated())
                {
                atype[i] = exprOld.getType();
                continue;
                }

            TypeConstant typeRequired = i < cReq ? atypeRequired[i] : null;
            if (typeRequired != null)
                {
                ctx = ctx.enterInferring(typeRequired);
                }

            Expression exprNew = exprOld.validate(ctx, typeRequired, errs);

            if (typeRequired != null)
                {
                ctx = ctx.exit();
                }

            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                if (exprNew != exprOld)
                    {
                    listExpr.set(i, exprNew);
                    }

                if (exprNew.isSingle())
                    {
                    atype[i] = exprNew.getType();
                    }
                else
                    {
                    if (cExprs == 1)
                        {
                        atype = exprNew.getTypes();
                        }
                    else if (i == cExprs - 1)
                        {
                        // this is the last expression, take the first value
                        atype[i] = exprNew.getType();
                        }
                    else
                        {
                        exprNew.log(errs, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT,
                                1, exprNew.getValueCount());
                        fValid = false;
                        }
                    }
                }
            }
        return fValid ? atype : null;
        }

    /**
     * Given an array of expressions representing actual parameters and the TypeInfo of the target,
     * find the best matching method.
     *
     * There is a difference in the way the default method parameters are handled depending on the
     * value of the "fCall" argument. In the case of a call, all the default method parameters that
     * are not explicitly specified are considered to be assigned to their default values. In the
     * case of a non-call, the default method parameters are treated in the same manner as others,
     * and any parameter that is not explicitly specified remains un-bound.
     * For example, having a function
     *      void foo(Int a, Int b = 0, Boolean c = False, Int d = 1)
     * a [call] expression "foo(1, c=True)" will result into a function call "foo(1, 0, True, 1)",
     * while the equivalent [bind] expression "&foo(1, c=True)" will result into a function of
     * type "function void (Int, Int)", where parameters "b" and "d" remain unbound.
     *
     * @param ctx           the compilation context
     * @param typeTarget    the type to search the method or function for
     * @param infoTarget    the type info on which to search for the method
     * @param sMethodName   the method name
     * @param listExprArgs  the expressions for arguments (which may not yet be validated)
     * @param kind          the kind of method to search for
     * @param fCall         if true, the method will be called; otherwise it will be bound
     * @param fAllowNested  if true, nested methods can be used at the target
     * @param atypeReturn   (optional) the array of return types from the method
     * @param errs          listener to log any errors to
     *
     * @return the MethodConstant for the desired method, or null if an exact match was not found,
     *         in which case an error has been reported
     */
    protected MethodConstant findMethod(Context ctx, TypeConstant typeTarget, TypeInfo infoTarget,
            String sMethodName, List<Expression> listExprArgs, MethodKind kind, boolean fCall,
            boolean fAllowNested, TypeConstant[] atypeReturn, ErrorListener errs)
        {
        assert sMethodName != null && sMethodName.length() > 0;

        int cExpr = listExprArgs == null ? 0 : listExprArgs.size();

        // collect available types for unnamed arguments and a map of named expressions
        Map<String, Expression> mapNamedExpr = cExpr > 0
                ? extractNamedArgs(listExprArgs, errs)
                : Collections.EMPTY_MAP;

        if (mapNamedExpr == null)
            {
            // an error has been reported
            return null;
            }

        int                 cArgs      = fCall ? cExpr : -1;
        Set<MethodConstant> setMethods = infoTarget.findMethods(sMethodName, cArgs, kind);
        ErrorListener       errsTemp   = errs.branch(this);

        if (fAllowNested)
            {
            IdentityConstant idScope = ctx.getMethod().getIdentityConstant();
            do
                {
                if (infoTarget.containsNestedMultiMethod(idScope, sMethodName))
                    {
                    Set<MethodConstant> setNested = infoTarget.findNestedMethods(
                                                        idScope, sMethodName, cArgs);
                    if (!setNested.isEmpty())
                        {
                        if (setMethods.isEmpty())
                            {
                            setMethods = setNested;
                            }
                        else
                            {
                            setMethods = new HashSet<>(setMethods);
                            setMethods.addAll(setNested);
                            }
                        }
                    }
                idScope = idScope.getNamespace();
                }
            while (idScope.isNested());
            }

        if (setMethods.isEmpty())
            {
            if (typeTarget.isSingleDefiningConstant() && typeTarget.getAccess() != Access.PRIVATE)
                {
                // check if there are any potentially matching private methods
                TypeConstant typePrivate = typeTarget.ensureAccess(Access.PRIVATE);
                if (!typePrivate.ensureTypeInfo(ErrorListener.BLACKHOLE).
                        findMethods(sMethodName, cArgs, kind).isEmpty())
                    {
                    log(errs, Severity.ERROR, Compiler.METHOD_INACCESSIBLE,
                            sMethodName, typeTarget.getValueString());
                    return null;
                    }
                }
            }
        else
            {
            // collect all theoretically matching methods
            Set<MethodConstant> setIs      = new HashSet<>();
            Set<MethodConstant> setConvert = new HashSet<>();

            collectMatchingMethods(ctx, typeTarget, infoTarget, setMethods, listExprArgs, fCall,
                    mapNamedExpr, atypeReturn, setIs, setConvert, errsTemp);

            // now choose the best match
            if (!setIs.isEmpty())
                {
                return chooseBest(setIs, typeTarget, errs);
                }

            if (!setConvert.isEmpty())
                {
                return chooseBest(setConvert, typeTarget, errs);
                }
            }

        if (errsTemp.hasSeriousErrors())
            {
            errsTemp.merge();
            }
        else
            {
            // if there was a problem with parameters or return values, collectMatchingMethods()
            // would have reported an error; simply report a miss as a backstop
            if (kind == MethodKind.Constructor)
                {
                log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, typeTarget.getValueString());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sMethodName, typeTarget.getValueString());
                }
            }
        return null;
        }

    /**
     * @return true iff the specified list contains a named argument
     */
    protected boolean containsNamedArgs(List<Expression> listExprArgs)
        {
        for (int i = 0, cExpr = listExprArgs.size(); i < cExpr; ++i)
            {
            Expression exprArg = listExprArgs.get(i);

            if (exprArg instanceof LabeledExpression)
                {
                return true;
                }
            }
        return false;
        }

    /**
     * Extract all named expression from the specified list into a map. Fill the implicit types
     * for all not-named arguments into the head of the type array.
     *
     * @return a map of named expressions; null if an error was reported
     */
    protected Map<String, Expression> extractNamedArgs(List<Expression> listExprArgs,
                                                       ErrorListener errs)
        {
        Map<String, Expression> mapNamed = null;

        for (int i = 0, cExpr = listExprArgs.size(); i < cExpr; ++i)
            {
            Expression exprArg = listExprArgs.get(i);

            if (exprArg instanceof LabeledExpression exprLabel)
                {
                String sName = exprLabel.getName();

                if (mapNamed == null)
                    {
                    mapNamed = new HashMap<>(cExpr);
                    }
                else
                    {
                    if (mapNamed.containsKey(sName))
                        {
                        exprArg.log(errs, Severity.ERROR, Compiler.NAME_COLLISION, sName);
                        return null;
                        }
                    }

                // extract the underlying expression
                mapNamed.put(sName, exprLabel.getUnderlyingExpression());
                }
            else
                {
                if (mapNamed != null)
                    {
                    exprArg.log(errs, Severity.ERROR, Compiler.ARG_NAME_REQUIRED, i);
                    return null;
                    }
                }
            }
        return mapNamed == null ? Collections.EMPTY_MAP : mapNamed;
        }

    /**
     * Helper method to collect matching methods.
     */
    private void collectMatchingMethods(
            Context                 ctx,
            TypeConstant            typeTarget,
            TypeInfo                infoTarget,
            Set<MethodConstant>     setMethods,
            List<Expression>        listExprArgs,
            boolean                 fCall,
            Map<String, Expression> mapNamedExpr,
            TypeConstant[]          atypeReturn,
            Set<MethodConstant>     setIs,
            Set<MethodConstant>     setConvert,
            ErrorListener           errs)
        {
        ConstantPool  pool     = pool();
        int           cExprs   = listExprArgs == null ? 0 : listExprArgs.size();
        int           cReturns = atypeReturn  == null ? 0 : atypeReturn.length;
        int           cNamed   = mapNamedExpr.size();
        ErrorListener errsTemp = errs.branch(this);
        ErrorListener errsKeep = null;

        // if there are multiple methods with the same name and no match is found, let's try to
        // report an error only if it's clear which method didn't fit:
        // - a wrong arity error should be reported only if there are no other errors
        // - a type mismatch error should be reported only if there are no other type mismatch errors
        int cArityErrs = 0;
        int cTypeErrs  = 0;

        NextMethod: for (MethodConstant idMethod : setMethods)
            {
            MethodInfo        infoMethod = infoTarget.getMethodById(idMethod);
            MethodStructure   method     = infoMethod.getTopmostMethodStructure(infoTarget);
            SignatureConstant sigMethod  = idMethod.getSignature();

            int cTypeParams = method.getTypeParamCount();
            int cVisible    = method.getVisibleParamCount();
            int cRequired   = method.getRequiredParamCount();

            if (cExprs > cVisible || fCall && cExprs < cRequired)
                {
                if (cArityErrs++ == 0 && cTypeErrs == 0)
                    {
                    errsKeep = errs.branch(this);
                    log(errsKeep, Severity.ERROR, Compiler.ARGUMENT_WRONG_COUNT, cRequired, cExprs);
                    }
                continue;
                }

            int cMethodRets = method.getReturnCount();
            if (cReturns > cMethodRets)
                {
                // the only allowed mismatch is a void method's return into an empty Tuple
                boolean fTuple = cReturns == 1 && isVoid(atypeReturn);
                if (cMethodRets != 0 || !fTuple)
                    {
                    if (cArityErrs++ == 0 && cTypeErrs == 0)
                        {
                        errsKeep = errs.branch(this);
                        log(errsKeep, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cReturns, cMethodRets);
                        }
                    continue;
                    }
                }

            List<Expression> listArgs = listExprArgs;
            int              cArgs    = cExprs;
            if (cArgs > 0 && cNamed > 0)
                {
                // insert the named expressions to the list of expressions in the correct position
                listArgs = rearrangeNamedArgs(method, listArgs, mapNamedExpr, errsTemp);
                if (listArgs == null)
                    {
                    // invalid name encountered
                    errsKeep = errsTemp;
                    continue;
                    }
                cArgs = listArgs.size();

                if (fCall)
                    {
                    // make sure all the required args are present
                    for (int i = 0; i < cRequired; i++)
                        {
                        if (listArgs.get(i) instanceof NonBindingExpression)
                            {
                            continue NextMethod;
                            }
                        }
                    }
                }

            TypeConstant[] atypeArgs = new TypeConstant[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                Expression exprArg = listArgs.get(i);

                atypeArgs[i] = exprArg.isValidated()
                                ? exprArg.getType()
                                : exprArg.getImplicitType(ctx);
                }

            // now let's assume that the method fits and based on that resolve the type parameters
            // and the method signature
            if (cTypeParams > 0)
                {
                atypeArgs = transformTypeArguments(ctx, method, listArgs, atypeArgs);

                ListMap<FormalConstant, TypeConstant> mapTypeParams =
                        resolveTypeParameters(method, atypeArgs, atypeReturn, true);
                if (mapTypeParams.size() < cTypeParams)
                    {
                    // different arguments/returns cause the formal type to resolve into
                    // incompatible types
                    continue;
                    }
                sigMethod = sigMethod.resolveGenericTypes(pool, GenericTypeResolver.of(mapTypeParams));
                }

            TypeConstant[] atypeParam = sigMethod.getRawParams();
            TypeFit        fit        = TypeFit.Fit;
            for (int i = 0; i < cArgs; ++i)
                {
                Expression   exprArg   = listArgs.get(i);
                TypeConstant typeParam = atypeParam[cTypeParams + i];
                TypeConstant typeArg   = atypeArgs[i];

                // check if the method's parameter type fits the argument expression
                if (typeParam != null)
                    {
                    ctx = ctx.enterInferring(typeParam);
                    }

                // while typeArg represents the expression's implicit type, obtain the
                // implicit type again now using the inferring context
                TypeConstant typeExpr = exprArg.isValidated()
                        ? exprArg.getType()
                        : exprArg.getImplicitType(ctx);
                if (typeExpr != null && typeExpr.isA(typeParam))
                    {
                    fit = TypeFit.Fit;
                    }
                else
                    {
                    // if *all* tests fail, report the errors from the first unsuccessful attempt
                    fit = exprArg.testFit(ctx, typeParam, true, errsTemp);
                    }
                if (fit.isFit())
                    {
                    // the challenge is that the inferred expression could be more forgiving
                    // than its original implicit type would suggest (e.g. NewExpression);
                    // for the type parameter resolution below, lets pick the narrowest type
                    if (typeArg == null)
                        {
                        typeArg = typeExpr;
                        }
                    else if (typeExpr != null && !typeArg.equals(typeExpr))
                        {
                        typeArg = typeArg .isA(typeExpr) ? typeArg
                                : typeExpr.isA(typeArg)  ? typeExpr
                                :                          null;
                        }
                    atypeArgs[i] = typeArg;
                    }
                else if (typeParam != null && !errsTemp.hasSeriousErrors())
                    {
                    if (exprArg instanceof NameExpression exprName)
                        {
                        typeExpr = exprName.getImplicitType(ctx, typeParam, ErrorListener.BLACKHOLE);
                        }

                    log(errsTemp, Severity.ERROR, Compiler.INCOMPATIBLE_PARAMETER_TYPE,
                            String.valueOf(i+1), method.getParam(i).getName(),
                            method.getIdentityConstant().getValueString(),
                            typeParam.getValueString(),
                            typeExpr == null ? exprArg.toString() : typeExpr.getValueString());
                    }

                if (typeParam != null)
                    {
                    ctx = ctx.exit();
                    }

                if (!fit.isFit())
                    {
                    break;
                    }
                }

            if (fit.isFit() && cTypeParams > 0)
                {
                // re-resolve the type parameters since we could have narrowed some
                ListMap<FormalConstant, TypeConstant> mapTypeParams =
                        resolveTypeParameters(method, atypeArgs, atypeReturn, true);
                if (mapTypeParams.size() < cTypeParams)
                    {
                    // different arguments/returns cause the formal type to resolve into
                    // incompatible types
                    log(errsTemp, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                            method.collectUnresolvedTypeParameters(mapTypeParams.keySet().
                                stream().map(NamedConstant::getName).collect(Collectors.toSet())));

                    fit = TypeFit.NoFit;
                    }
                else
                    {
                    sigMethod = sigMethod.resolveGenericTypes(pool, GenericTypeResolver.of(mapTypeParams));
                    }
                }

            if (fit.isFit() && cReturns > 0)
                {
                fit = calculateReturnFit(sigMethod, fCall, atypeReturn, typeTarget, errsTemp);
                }

            if (!fit.isFit())
                {
                if (errsTemp.hasSeriousErrors())
                    {
                    if (cTypeErrs++ == 0)
                        {
                        errsKeep = errsTemp;
                        }

                    errsTemp = errs.branch(this);
                    }
                continue; // NextMethod
                }

            if (fit.isConverting())
                {
                setConvert.add(idMethod);
                }
            else
                {
                setIs.add(idMethod);
                }
            }

        // if there is any ambiguity, don't report anything; the caller will log a generic
        // "Could not find a matching method" error
        if (cTypeErrs == 1 || (cTypeErrs == 0 && cArityErrs == 1))
            {
            errsKeep.merge();
            }
        }

    /**
     * Iterate over the specified argument list, and transform all canonical <code>Type<></code>
     * types to the corresponding dynamic types.
     */
    protected TypeConstant[] transformTypeArguments(Context ctx, MethodStructure method,
                                                    List<Expression> listArgs, TypeConstant[] atypeArgs)
        {
        assert listArgs.size() == atypeArgs.length;

        TypeConstant typeObj = pool().typeObject();
        for (int i = 0, cArgs = atypeArgs.length; i < cArgs; i++)
            {
            TypeConstant type = atypeArgs[i];
            if (type != null && type.isTypeOfType() && type.getParamType(0).equals(typeObj) &&
                    listArgs.get(i) instanceof NameExpression exprName &&
                        exprName.getMeaning() == Meaning.Variable)
                {
                type = transformType(ctx, method, exprName);
                }

            atypeArgs[i] = type;
            }
        return atypeArgs;
        }

    /**
     * Given a NameExpression whose type is <code>Type<></code>, transform it to a dynamic type
     * constant <code>Type<[name].DataType></code>.
     */
    protected TypeConstant transformType(Context ctx, MethodStructure method, NameExpression exprName)
        {
        ConstantPool pool = pool();
        TypeConstant type = pool.typeType();
        Argument     arg  = exprName.resolveRawArgument(ctx, false, ErrorListener.BLACKHOLE);
        if (arg instanceof Register reg)
            {
            PropertyConstant idProp   = type.ensureTypeInfo().findProperty("DataType").getIdentity();
            FormalConstant   idFormal = pool.ensureDynamicFormal(
                method.getIdentityConstant(), reg, idProp, exprName.getName());

            type = pool.ensureParameterizedTypeConstant(type, idFormal.getType());
            }
        return type;
        }

    /**
     * A trivial wrapper around {@link MethodStructure#resolveTypeParameters} call that allows
     * subclasses to override it.
     */
    protected ListMap<FormalConstant, TypeConstant> resolveTypeParameters(MethodStructure method,
            TypeConstant[] atypeArgs, TypeConstant[] atypeReturn, boolean fAllowFormal)
        {
        return method.resolveTypeParameters(null, atypeArgs, atypeReturn, fAllowFormal);
        }

    /**
     * Rearrange the list of argument expressions for the specified method.
     * All the missing arguments are filled with a {@link NonBindingExpression}s.
     *
     * @return a rearranged list of expression that matches the method's parameters
     *         or null if an error has been reported
     */
    protected List<Expression> rearrangeNamedArgs(
            MethodStructure method, List<Expression> listExprArgs, ErrorListener errs)
        {
        Map<String, Expression> mapNamedExpr = extractNamedArgs(listExprArgs, errs);
        return mapNamedExpr == null
                ? null
                : rearrangeNamedArgs(method, listExprArgs, mapNamedExpr, errs);
        }

    /**
     * Rearrange the list of argument expressions for the specified method by taking into
     * consideration the map of named expressions. All the missing arguments are filled with
     * a {@link NonBindingExpression}s.
     *
     * @return a rearranged list of expression that matches the method's parameters
     *         or null if an error has been reported
     */
    protected List<Expression> rearrangeNamedArgs(
            MethodStructure method, List<Expression> listExprArgs,
            Map<String, Expression> mapNamedExpr, ErrorListener errs)
        {
        int cParams  = method.getVisibleParamCount();
        int cArgs    = listExprArgs.size();
        int cNamed   = mapNamedExpr.size();
        int cUnnamed = cArgs - cNamed;

        Expression[] aexpr = new Expression[cParams];

        // fill the head of the array with unnamed expressions
        for (int i = 0; i < cUnnamed; i++)
            {
            aexpr[i] = listExprArgs.get(i);
            }

        for (String sName : mapNamedExpr.keySet())
            {
            org.xvm.asm.Parameter param = method.getParam(sName);
            if (param == null)
                {
                log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, sName);
                return null;
                }
            int iParam = param.getIndex();

            // if a named arg overrides an unnamed (required), we'll null it out to generate
            // an error later
            aexpr[iParam] = iParam >= cUnnamed ? mapNamedExpr.get(sName) : null;
            }

        // replace non-specified "holes" with NonBindingExpressions
        long lPos = getStartPosition();
        for (int i = cUnnamed; i < cParams; i++)
            {
            if (aexpr[i] == null)
                {
                NonBindingExpression exprNB = new NonBindingExpression(lPos, lPos, null);
                adopt(exprNB);
                exprNB.setStage(Stage.Validated);
                aexpr[i] = exprNB;
                }
            }

        return Arrays.asList(aexpr);
        }

    /**
     * Choose the best fit out of a non-empty set of methods.
     *
     * @param setMethods  the non-empty set of methods
     * @param typeTarget  the target type
     * @param errs        the error list to log to
     *
     * @return the best matching method or null, if the methods are ambiguous, in which case
     *         an error has been reported
     */
    protected MethodConstant chooseBest(Set<MethodConstant> setMethods,
                                        TypeConstant typeTarget, ErrorListener errs)
        {
        assert !setMethods.isEmpty();

        MethodConstant    idBest  = null;
        SignatureConstant sigBest = null;
        for (MethodConstant idMethod : setMethods)
            {
            SignatureConstant sigMethod = idMethod.getSignature();
            if (idBest == null)
                {
                idBest  = idMethod;
                sigBest = sigMethod;
                }
            else
                {
                boolean fOldBetter;
                boolean fNewBetter;

                int cParamsOld = sigBest.getParamCount();
                int cParamsNew = sigMethod.getParamCount();
                if (cParamsOld == cParamsNew)
                    {
                    fOldBetter = sigMethod.isSubstitutableFor(sigBest, typeTarget);
                    fNewBetter = sigBest.isSubstitutableFor(sigMethod, typeTarget);

                    if (!fOldBetter && !fNewBetter)
                        {
                        // choose the one that is narrower for every argument
                        for (int i = 0; i < cParamsOld; i++)
                            {
                            TypeConstant typeOld = sigBest.getRawParams()[i];
                            TypeConstant typeNew = sigMethod.getRawParams()[i];

                            if (typeOld.isA(typeNew) && !typeNew.isA(typeOld))
                                {
                                fOldBetter = true;
                                }
                            if (typeNew.isA(typeOld) && !typeOld.isA(typeNew))
                                {
                                fNewBetter = true;
                                }

                            if (fNewBetter && fOldBetter)
                                {
                                // new was better at some but not other arg; still ambiguous
                                fOldBetter = fNewBetter = false;
                                break;
                                }
                            }
                        }
                    }
                else
                    {
                    // there are two methods that match, but one has fewer parameters that the
                    // other, which means that the one with more parameters has default values;
                    // therefore, we could safely choose the method with fewer parameters
                    fNewBetter = cParamsNew < cParamsOld;
                    fOldBetter = !fNewBetter;
                    }

                if (fOldBetter || fNewBetter)
                    {
                    // if both are substitutable to each other, we can take any
                    // (though we could also get the target's info, find the corresponding
                    //  TypeInfo and choose a more accommodating MethodInfo)
                    if (fNewBetter)
                        {
                        idBest  = idMethod;
                        sigBest = sigMethod;
                        }
                    }
                else
                    {
                    // note: theoretically there could still be one better than either of these two,
                    // but for now, just assume it's an error at this point
                    log(errs, Severity.ERROR, Compiler.SIGNATURE_AMBIGUOUS,
                            idBest.getSignature().getValueString());
                    return null;
                    }
                }
            }
        return idBest;
        }


    /**
     * Calculate the fit for the method return values.
     *
     * @param sigMethod    the method signature
     * @param fCall        if true, the method will be called; otherwise it will be bound
     * @param atypeReturn  the array of required return types
     * @param typeCtx      the type within which context the covariance is to be determined
     * @param errs         listener to log any errors to
     *
     * @return a TypeFit value
     */
    protected TypeFit calculateReturnFit(SignatureConstant sigMethod, boolean fCall,
                                         TypeConstant[] atypeReturn, TypeConstant typeCtx,
                                         ErrorListener errs)
        {
        return calculateReturnFit(sigMethod.getRawReturns(), sigMethod.getValueString(), fCall,
                                    atypeReturn, typeCtx, errs);
        }

    /**
     * Calculate the fit for a method or function return values.
     *
     * @param atypeMethodReturn  the method return types
     * @param sName              a method or function name or signature; used for error reporting only
     * @param fCall              if true, the method will be called; otherwise it will be bound
     * @param atypeReturn        the array of required return types
     * @param typeCtx            the type within which context the covariance is to be determined
     * @param errs               listener to log any errors to
     *
     * @return a TypeFit value
     */
    protected TypeFit calculateReturnFit(TypeConstant[] atypeMethodReturn, String sName,
                                         boolean fCall, TypeConstant[] atypeReturn,
                                         TypeConstant typeCtx, ErrorListener errs)
        {
        int      cMethodReturns = atypeMethodReturn.length;
        int      cReturns       = atypeReturn.length;
        TypeFit  fit            = TypeFit.Fit;

        if (cMethodReturns < cReturns)
            {
            // we allow such an "asymmetrical" call in just one case:
            // - a "void" method return is allowed to be assigned to an empty Tuple
            //    void f() {...}
            //    Tuple v = f();
            // (also see ConstantPool.checkFunctionOrMethodCompatibility)
            if (cMethodReturns == 0 && cReturns == 1 && isVoid(atypeReturn))
                {
                return TypeFit.Pack;
                }

            log(errs, Severity.ERROR, Compiler.INCOMPATIBLE_RETURN_COUNT,
                   sName, String.valueOf(cReturns), String.valueOf(cMethodReturns));
            return TypeFit.NoFit;
            }

        if (cMethodReturns > cReturns)
            {
            // we allow such an "asymmetrical" call in just one case:
            // - a non-void method return into a matching Tuple:
            //    (Int, String) f() {...}
            //    Tuple<Int, String> t = f();
            if (cReturns == 1 && atypeReturn[0].isTuple())
                {
                fit         = fit.addPack();
                atypeReturn = atypeReturn[0].getParamTypesArray();
                cReturns    = atypeReturn.length;
                }

            if (cMethodReturns < cReturns)
                {
                log(errs, Severity.ERROR, Compiler.INCOMPATIBLE_RETURN_COUNT,
                       sName, String.valueOf(cReturns), String.valueOf(cMethodReturns));
                return TypeFit.NoFit;
                }
            }

        for (int i = 0; i < cReturns; i++)
            {
            TypeConstant typeReturn       = atypeReturn[i];
            TypeConstant typeMethodReturn = atypeMethodReturn[i];

            if (!typeMethodReturn.isCovariantReturn(typeReturn, typeCtx))
                {
                if (typeMethodReturn.getConverterTo(typeReturn) != null)
                    {
                    fit = fit.addConversion();
                    }
                else
                    {
                    // there is one more scenario, when types may not be assignable, but still fit:
                    // - a single value return into a matching Tuple:
                    //    Int f() {...}
                    //    Tuple t = f();
                    if (fCall && cReturns == 1 && cMethodReturns == 1
                        && typeReturn.isTuple() && typeReturn.getParamsCount() <= 1
                        && typeMethodReturn.isCovariantReturn(typeReturn.getParamType(0), typeCtx))
                        {
                        return TypeFit.Pack;
                        }

                    log(errs, Severity.ERROR, Compiler.INCOMPATIBLE_RETURN_TYPE,
                            sName, typeReturn.getValueString(), typeMethodReturn.getValueString());
                    return TypeFit.NoFit;
                    }
                }
            }
        return fit;
        }

    /**
     * @return true iff all the specified types are {@link PendingTypeConstant}
     */
    protected static boolean isPending(TypeConstant... atype)
        {
        for (TypeConstant type : atype)
            {
            if (!(type instanceof PendingTypeConstant))
                {
                return false;
                }
            }
        return true;
        }

    /**
     * @return true iff all the specified types are equal to the "void" tuple
     */
    protected boolean isVoid(TypeConstant... atype)
        {
        for (TypeConstant type : atype)
            {
            if (!type.isTuple() || type.getParamsCount() > 0)
                {
                return false;
                }
            }
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    public String toDumpString()
        {
        StringWriter sw = new StringWriter();
        dump(new PrintWriter(sw), "", "");
        return sw.toString();
        }

    public void dump()
        {
        dump(new PrintWriter(System.out, true), "", "");
        }

    protected void dump(PrintWriter out, String sIndentFirst, String sIndent)
        {
        // find the children to dump, but prune out any empty categories
        Map<String, Object> cats = getDumpChildren();
        for (Iterator<Map.Entry<String, Object>> iter = cats.entrySet().iterator(); iter.hasNext(); )
            {
            Map.Entry<String, Object> entry = iter.next();

            Object value = entry.getValue();
            if (value == null)
                {
                iter.remove();
                }
            else if (value instanceof Map map)
                {
                if (map.isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection coll)
                {
                if (coll.isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Object[] ao)
                {
                if (ao.length == 0)
                    {
                    iter.remove();
                    }
                }
            }

        // print out a line of info about this node (if more than one line is necessary, then indent
        // the whole thing under the top line)
        if (sIndentFirst.length() > 0)
            {
            out.print(sIndentFirst + "- ");
            }
        out.print(getClass().getSimpleName());

        String sThis = getDumpDesc();
        if (sThis == null || sThis.length() == 0)
            {
            out.println();
            }
        else if (sThis.indexOf('\n') < 0)
            {
            out.println(": " + sThis);
            }
        else
            {
            out.println();
            out.println(indentLines(sThis, sIndent + (cats.isEmpty() ? "      " : " |    ")));
            }

        // for each category, print the category name, then print the child nodes under it; if
        // there's only one child node, then just stick it on the same line
        String sIndentCat   = sIndent + "   |- ";
        String sIndentKid   = sIndent + "   |   |";         // all kids except last cat
        String sIndentLastC = sIndent + "       |";         // last cat kids except last kid
        String sIndentLastK = sIndent + "        ";         // last kid on last cat
        int cCats = 0;
        for (Iterator<Map.Entry<String, Object>> iterC = cats.entrySet().iterator(); iterC.hasNext(); )
            {
            boolean                   fLastC = (++cCats == cats.size());
            Map.Entry<String, Object> entry  = iterC.next();
            String                    sCat   = entry.getKey();
            Object                    value  = entry.getValue();

            // category name
            out.print(sIndentCat + sCat);

            // find the kids
            int      cKids;
            Iterator iterK;
            if (value instanceof Map kids)
                {
                cKids = kids.size();
                iterK = kids.entrySet().iterator();
                }
            else if (value instanceof Collection kids)
                {
                cKids = kids.size();
                iterK = kids.iterator();
                }
            else if (value instanceof Object[] kids)
                {
                cKids = kids.length;
                iterK = Arrays.asList(kids).iterator();
                }
            else
                {
                cKids = 1;
                iterK = Collections.singletonList(value).iterator();
                }

            for (int i = 0; i < cKids; ++i)
                {
                Object  kid      = iterK.next();
                boolean fFirstK  = (i == 0);
                boolean fLastK   = (i == cKids - 1);
                String  sIndent1 = fLastC ? sIndentLastC : sIndentKid;
                String  sIndent2 = fLastC ? (fLastK ? sIndentLastK : sIndentLastC) : sIndentKid;

                if (kid instanceof AstNode node)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    node.dump(out, sIndent1, sIndent2);
                    }
                else if (kid instanceof Map.Entry)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    throw new UnsupportedOperationException("TODO");
                    }
                else // any old object
                    {
                    String sKid = String.valueOf(kid);
                    if (sKid.indexOf('\n') < 0)
                        {
                        if (cKids == 1)
                            {
                            out.print(": ");
                            }
                        else
                            {
                            if (fFirstK)
                                {
                                out.println();
                                }
                            out.print(sIndent1 + "- ");
                            }
                        }
                    else
                        {
                        if (fFirstK)
                            {
                            out.println();
                            }
                        sKid = indentLines(sKid, sIndent2 + "  ");
                        sKid = sIndent1 + "- " + sKid.substring(sIndent1.length() + 2);
                        }
                    out.println(sKid);
                    }
                }
            }
        }

    /**
     * TODO doc
     *
     * @return
     */
    public String getDumpDesc()
        {
        return null;
        }

    /**
     * Build and return a map that allows the caller to navigate the children of this node.
     * <p/>
     * Assume some type T which represents either an AstNode instance, or an object that implements
     * toString(). The keys of the map should be strings that describe the categories of the
     * children, while the values should provide the info about the children of this AstNode,
     * either as an object of type T, a Collection of type T, an array of type T, or a Map whose
     * keys and values are of type T.
     *
     * @return a map containing all the child information to dump
     */
    public Map<String, Object> getDumpChildren()
        {
        Field[] fields = getChildFields();
        if (fields.length == 0)
            {
            return Collections.EMPTY_MAP;
            }

        Map<String, Object> map = new ListMap<>();
        for (Field field : fields)
            {
            try
                {
                map.put(field.getName(), field.get(this));
                }
            catch (IllegalAccessException e)
                {
                throw new IllegalStateException(e);
                }
            }
        return map;
        }


    // ----- internal -------------------------------------------------------------------

    /**
     * TODO delete this method when done
     *
     * @return nothing, because the method always throws
     * @throws UnsupportedOperationException this exception is always thrown by this method
     */
    protected UnsupportedOperationException notImplemented()
        {
        throw new UnsupportedOperationException("not implemented by: " + this.getClass().getSimpleName());
        }

    /**
     * Ensure that the passed list is an ArrayList, replacing it with an ArrayList if necessary.
     *
     * @param list  a List
     *
     * @return an ArrayList
     */
    protected static <T> ArrayList<T> ensureArrayList(List<T> list)
        {
        return list instanceof ArrayList alist
                ? alist
                : new ArrayList<>(list);
        }

    /**
     * Collect fields by name.
     *
     * @param clz    the class on which the fields exist
     * @param names  the field names
     *
     * @return an array of fields corresponding to the specified names on the specified class
     */
    protected static Field[] fieldsForNames(Class clz, String... names)
        {
        if (names == null || names.length == 0)
            {
            return NO_FIELDS;
            }

        Field[] fields = new Field[names.length];
        NextField: for (int i = 0, c = fields.length; i < c; ++i)
            {
            Class                clzTry = clz;
            NoSuchFieldException eOrig  = null;
            while (clzTry != null)
                {
                try
                    {
                    Field field = clzTry.getDeclaredField(names[i]);
                    assert field != null;
                    if (!field.getType().isInstance(AstNode.class) && field.getType().isInstance(List.class))
                        {
                        throw new IllegalStateException("unsupported field type "
                                + field.getType().getSimpleName() + " on field "
                                + clzTry.getSimpleName() + '.' + names[i]);
                        }
                    fields[i] = field;
                    continue NextField;
                    }
                catch (NoSuchFieldException e)
                    {
                    if (eOrig == null)
                        {
                        eOrig = e;
                        }

                    clzTry = clzTry.getSuperclass();
                    if (clz == null)
                        {
                        throw new IllegalStateException(eOrig);
                        }
                    }
                catch (SecurityException e)
                    {
                    throw new IllegalStateException(e);
                    }
                }
            }

        return fields;
        }


    // ----- inner class: ChildIterator ------------------------------------------------------------

    /**
     * Represents an Iterator that can also replace the most recently iterated element.
     */
    public interface ChildIterator
            extends Iterable<AstNode>, Iterator<AstNode>
        {
        @Override
        default Iterator<AstNode> iterator()
            {
            return this;
            }

        /**
         * Replace the most recently returned node with the specified new node.
         *
         * @param nodeNew  the node to use as a replacement for the node most recently returned from
         *                 the {@link #next()} method
         */
        default void replaceWith(AstNode nodeNew)
            {
            throw new IllegalStateException();
            }

        ChildIterator EMPTY = new ChildIterator()
            {
            @Override
            public boolean hasNext()
                {
                return false;
                }

            @Override
            public AstNode next()
                {
                throw new NoSuchElementException();
                }
            };
        }

    protected final class ChildIteratorImpl
            implements ChildIterator
        {
        /**
         * Construct a ChildIterator that will iterate all the children that are held in the
         * specified fields, which are either AstNodes themselves, or are container types thereof.
         *
         * @param fields  an array of fields of the AstNode
         */
        private ChildIteratorImpl(Field[] fields)
            {
            this.fields = fields;
            }

        public boolean hasNext()
            {
            return state == HAS_NEXT || prepareNextElement();
            }

        public AstNode next()
            {
            if (state == HAS_NEXT || prepareNextElement())
                {
                state = HAS_PREV;
                if (value instanceof AstNode node)
                    {
                    return node;
                    }
                else
                    {
                    return (AstNode) ((Iterator) value).next();
                    }
                }

            throw new NoSuchElementException();
            }

        private boolean prepareNextElement()
            {
            if (value instanceof Iterator iter && iter.hasNext())
                {
                state = HAS_NEXT;
                return true;
                }

            boolean prepped = prepareNextField();
            state = prepped ? HAS_NEXT : NOT_PREP;
            return prepped;
            }

        private boolean prepareNextField()
            {
            while (++iField < fields.length)
                {
                Object next;
                try
                    {
                    next = fields[iField].get(AstNode.this);
                    }
                catch (NullPointerException e)
                    {
                    throw new IllegalStateException(
                            "class=" + AstNode.this.getClass().getSimpleName()
                                    + ", field=" + iField);
                    }
                catch (IllegalAccessException e)
                    {
                    throw new IllegalStateException(e);
                    }

                if (next != null)
                    {
                    if (next instanceof List list)
                        {
                        if (!list.isEmpty())
                            {
                            value = list.listIterator();
                            return true;
                            }
                        }
                    else if (next instanceof Collection coll)
                        {
                        if (!coll.isEmpty())
                            {
                            value = coll.iterator();
                            return true;
                            }
                        }
                    else
                        {
                        assert next instanceof AstNode;
                        value = next;
                        return true;
                        }
                    }
                }

            value = null;
            return false;
            }

        public void remove()
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // null out the field
                    try
                        {
                        fields[iField].set(AstNode.this, null);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    state = NOT_PREP;
                    return;
                    }

                if (value instanceof Iterator iter)
                    {
                    // tell the underlying iterator to remove the value
                    iter.remove();
                    state = NOT_PREP;
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        public void replaceWith(AstNode newChild)
            {
            if (state == HAS_PREV)
                {
                if (value instanceof AstNode)
                    {
                    // the field holds a single node; store the new value
                    try
                        {
                        fields[iField].set(AstNode.this, newChild);
                        }
                    catch (IllegalAccessException e)
                        {
                        throw new IllegalStateException(e);
                        }
                    return;
                    }

                if (value instanceof ListIterator iter)
                    {
                    iter.set(newChild);
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        private static final int NOT_PREP = 0;
        private static final int HAS_NEXT = 1;
        private static final int HAS_PREV = 2;

        private final Field[] fields;
        private int iField = -1;
        private Object value;
        private int state = NOT_PREP;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Constant empty array  of fields.
     */
    protected static final Field[] NO_FIELDS = new Field[0];

    /**
     * The stage of compilation.
     */
    private Stage m_stage = Stage.Initial;

    /**
     * The parent of this AstNode.
     */
    private AstNode m_parent;
    }