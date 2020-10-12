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

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Source;

import org.xvm.compiler.ast.Expression.TypeFit;

import org.xvm.util.ListMap;
import org.xvm.util.Severity;

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
     * Return an Iterable/Iterator that represents all of the child nodes of this node.
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
                if (oVal instanceof AstNode)
                    {
                    AstNode nodeNew = ((AstNode) oVal).clone();

                    that.adopt(nodeNew);
                    oVal = nodeNew;
                    }
                else if (oVal instanceof List)
                    {
                    List<AstNode>      listOld = (List<AstNode>) oVal;
                    ArrayList<AstNode> listNew = new ArrayList<>();
                    for (AstNode node : listOld)
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
     * @param aTypes  the type of the RValue
     */
    public void updateLValueFromRValueTypes(Context ctx, TypeConstant[] aTypes)
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
        return true;
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
     * This must be overridden by any AST node that supports short circuiting children. This is
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
     * Collect all of the expressions that should be included in tracing debug output. This is used
     * by "assert", for example, to display information relevant to the assertion.
     *
     * @param mapExprs  the expressions collected thus far, keyed by their source code
     *                  representations
     */
    protected void selectTraceableExpressions(Map<String, Expression> mapExprs)
        {
        for (AstNode node : children())
            {
            if (node instanceof Expression)
                {
                Expression expr = (Expression) node;
                if (expr.isValidated() && expr.isTraceworthy())
                    {
                    String sExpr = expr.toString();
                    if (!mapExprs.containsKey(sExpr))
                        {
                        mapExprs.put(sExpr, expr);
                        }
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
     *         false to attempt continue the process
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
        // this point, recreating the various compiler stages here; start by collecting all of the
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

        while (stageOldest.compareTo(stageTarget) < 0)
            {
            Stage    stageNext = stageOldest.nextTarget();
            StageMgr mgrKids   = new StageMgr(listChildren, stageNext, errs);
            while (!mgrKids.processComplete())
                {
                if (errs.isAbortDesired() || mgrKids.getIterations() > 20)
                    {
                    mgrKids.logDeferredAsErrors(errs);
                    return false;
                    }
                }

            stageOldest = stageNext;
            }

        return true;
        }

    // ----- name resolution -----------------------------------------------------------------------

    /**
     * Determine if this particular node has an import registered on it of the specified name.
     *
     * @param sName  a simple name
     *
     * @return an ImportStatement, or null
     */
    protected ImportStatement resolveImportBySingleName(String sName)
        {
        AstNode parent = getParent();
        return parent == null ? null : parent.resolveImportBySingleName(sName);
        }

    /**
     * @return true iff this AstNode should be able to resolve names
     */
    protected boolean canResolveNames()
        {
        if (this instanceof NameResolver.NameResolving)
            {
            // the problem is this: that a NameResolver that hasn't been invoked as part of the
            // natural pass of the resolveNames() recursion has not had a chance to figure out what
            // the effect its imports may have on name resolution, and thus we can't ask it what a
            // name means
            return !((NameResolver.NameResolving) this).getNameResolver().isFirstTime();
            }

        // for all other components (that don't override this method because they know more about
        // whether or not they can resolve names), we'll assume that if they haven't been resolved,
        // then they don't know how to resolve names
        return alreadyReached(Stage.Resolving);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Test fit for the specified expressions against the specified types.
     *
     * @param ctx        the compiler context
     * @param listExpr   the list of expressions
     * @param atypeTest  the types array to test fit for
     *
     * @return the combine fit for all the expressions
     */
    protected TypeFit testExpressions(Context ctx, List<Expression> listExpr,
                                      TypeConstant[] atypeTest)
        {
        int     cTypes = atypeTest == null ? 0 : atypeTest.length;
        TypeFit fit    = TypeFit.Fit;
        for (int i = 0, c = Math.min(listExpr.size(), cTypes); i < c ; ++i)
            {
            TypeConstant typeTest = atypeTest[i];
            if (typeTest != null)
                {
                ctx = ctx.enterInferring(typeTest);
                }

            fit = fit.combineWith(listExpr.get(i).testFit(ctx, typeTest, null));

            if (typeTest != null)
                {
                ctx = ctx.exit();
                }
            }
        return fit;
        }

    /**
     * Validate the specified expressions against the required types.
     *
     * @param ctx            the compiler context
     * @param listExpr       the list of expressions (may be modified)
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
                if (!exprNew.isSingle())
                    {
                    if (cExprs == 1)
                        {
                        atype = exprNew.getTypes();
                        }
                    else
                        {
                        fValid = false;
                        }
                    }
                else
                    {
                    atype[i] = exprNew.getType();
                    }
                }
            }
        return fValid ? atype : null;
        }

    /**
     * Validate the specified expressions against the required types.
     *
     * @param ctx         the compiler context
     * @param listExpr    the list of expressions (may be modified)
     * @param typeTuple   the required tuple type
     * @param errs        the error listener
     *
     * @return an array of TypeConstants describing the actual expression types or null
     *         if the validation fails, in which case an error has been reported
     */
    protected TypeConstant[] validateExpressionsFromTuple(Context ctx, List<Expression> listExpr,
                                                          TypeConstant typeTuple, ErrorListener errs)
        {
        Expression exprOld = listExpr.get(0);
        Expression exprNew = exprOld.validate(ctx, typeTuple, errs);
        if (exprNew == null)
            {
            // validation failed
            return null;
            }

        if (exprOld != exprNew)
            {
            listExpr.set(0, exprNew);
            }
        return exprNew.getType().getParamTypesArray();
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
    protected MethodConstant findMethod(
            Context          ctx,
            TypeConstant     typeTarget,
            TypeInfo         infoTarget,
            String           sMethodName,
            List<Expression> listExprArgs,
            MethodKind       kind,
            boolean          fCall,
            boolean          fAllowNested,
            TypeConstant[]   atypeReturn,
            ErrorListener    errs)
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

        // allow for a Tuple based invocation; for example a method f(String s, Int i)
        // is invocable via f(t), where "t" is an argument of type Tuple<String, Int>
        TypeConstant typeTupleArg = null;
        if (fCall && cExpr == 1 && mapNamedExpr.isEmpty())
            {
            Expression   exprTuple = listExprArgs.get(0);
            TypeConstant typeTuple = exprTuple.isValidated()
                    ? exprTuple.getType()
                    : exprTuple.getImplicitType(ctx);

            if (typeTuple != null && typeTuple.isTuple() && !typeTuple.isFormalType())
                {
                typeTupleArg = typeTuple;
                }
            }

        int                 cArgs      = fCall ? cExpr : -1;
        Set<MethodConstant> setMethods = infoTarget.findMethods(sMethodName, cArgs, kind);

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
            if (kind == MethodKind.Constructor)
                {
                log(errs, Severity.ERROR, Compiler.MISSING_CONSTRUCTOR, typeTarget.getValueString());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sMethodName, typeTarget.getValueString());
                }
            return null;
            }

        // collect all theoretically matching methods
        Set<MethodConstant> setIs      = new HashSet<>();
        Set<MethodConstant> setConvert = new HashSet<>();
        ErrorListener       errsTemp   = errs.branch();

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

        if (typeTupleArg != null)
            {
            setMethods = infoTarget.findMethods(sMethodName, typeTupleArg.getParamsCount(), kind);

            ErrorListener errsTempT = errs.branch();
            collectMatchingMethods(ctx, typeTarget, infoTarget, setMethods, listExprArgs, fCall,
                    mapNamedExpr, atypeReturn, setIs, setConvert, errsTempT);

            if (!setIs.isEmpty())
                {
                return chooseBest(setIs, typeTarget, errs);
                }

            if (!setConvert.isEmpty())
                {
                return chooseBest(setConvert, typeTarget, errs);
                }

            if (!errsTemp.hasSeriousErrors())
                {
                errsTempT.merge();
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

            if (exprArg instanceof LabeledExpression)
                {
                String sName = ((LabeledExpression) exprArg).getName();

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
                mapNamed.put(sName, exprArg);
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
        int           cArgs    = listExprArgs == null ? 0 : listExprArgs.size();
        int           cReturns = atypeReturn  == null ? 0 : atypeReturn.length;
        int           cNamed   = mapNamedExpr.size();
        ErrorListener errsTemp = errs.branch();
        ErrorListener errsKeep = null;

        NextMethod: for (MethodConstant idMethod : setMethods)
            {
            MethodInfo        infoMethod = infoTarget.getMethodById(idMethod);
            MethodStructure   method     = infoMethod.getTopmostMethodStructure(infoTarget);
            SignatureConstant sigMethod  = idMethod.getSignature();

            int cTypeParams = method.getTypeParamCount();
            int cParams     = method.getVisibleParamCount();
            int cDefaults   = method.getDefaultParamCount();
            int cRequired   = cParams - cDefaults;

            if (cArgs > cParams || fCall && cArgs < cRequired)
                {
                // invalid number of arguments
                continue;
                }

            if (cNamed > 0)
                {
                // insert the named expressions to the list of expressions in the correct position
                listExprArgs = rearrangeNamedArgs(method, listExprArgs, mapNamedExpr, errsTemp);
                if (listExprArgs == null)
                    {
                    // invalid name encountered
                    errsKeep = errsTemp;
                    continue;
                    }
                cArgs = listExprArgs.size();

                if (fCall)
                    {
                    // make sure all the required args are present
                    for (int i = 0; i < cRequired; i++)
                        {
                        if (listExprArgs.get(i) instanceof NonBindingExpression)
                            {
                            continue NextMethod;
                            }
                        }
                    }
                }

            TypeConstant[] atypeArgs = new TypeConstant[cArgs];
            for (int i = 0; i < cArgs; i++)
                {
                Expression exprArg = listExprArgs.get(i);

                atypeArgs[i] = exprArg.isValidated()
                                ? exprArg.getType()
                                : exprArg.getImplicitType(ctx);
                }

            // now let's assume that the method fits and based on that resolve the type parameters
            // and the method signature
            if (cTypeParams > 0)
                {
                ListMap<String, TypeConstant> mapTypeParams =
                        method.resolveTypeParameters(atypeArgs, atypeReturn, true);
                if (mapTypeParams.size() < cTypeParams)
                    {
                    // different arguments/returns cause the formal type to resolve into
                    // incompatible types
                    continue;
                    }
                sigMethod = sigMethod.resolveGenericTypes(pool, mapTypeParams::get);
                }

            TypeConstant[] atypeParam = sigMethod.getRawParams();
            TypeFit        fit        = TypeFit.Fit;
            for (int i = 0; i < cArgs; ++i)
                {
                Expression   exprArg   = listExprArgs.get(i);
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

                // if *all* tests fail, report the errors from the first unsuccessful attempt
                fit = exprArg.testFit(ctx, typeParam, errsTemp);
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
                else if (typeParam != null)
                    {
                    log(errsTemp, Severity.ERROR, Compiler.INCOMPATIBLE_PARAMETER_TYPE,
                            sigMethod.getName(),
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
                ListMap<String, TypeConstant> mapTypeParams =
                        method.resolveTypeParameters(atypeArgs, atypeReturn, true);
                if (mapTypeParams.size() < cTypeParams)
                    {
                    // different arguments/returns cause the formal type to resolve into
                    // incompatible types
                    log(errsTemp, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE,
                            method.collectUnresolvedTypeParameters(mapTypeParams.keySet()));
                    fit = TypeFit.NoFit;
                    }
                else
                    {
                    sigMethod = idMethod.getSignature().resolveGenericTypes(pool, mapTypeParams::get);
                    }
                }

            if (fit.isFit() && cReturns > 0)
                {
                fit = calculateReturnFit(sigMethod, fCall, atypeReturn, ctx.getThisType(), errsTemp);
                }

            if (!fit.isFit())
                {
                if (errsTemp.hasSeriousErrors())
                    {
                    // once we find something bad enough to stop compilation, that becomes our
                    // "errors to report in case there is no match", but we keep looking for a
                    // method that fits
                    if (errsKeep == null)
                        {
                        errsKeep = errsTemp;
                        }
                    else
                        {
                        // discard the errors
                        errsTemp = errs.branch();
                        }
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

        if (errsKeep != null)
            {
            // copy the most serious errors even if we found some matching methods; there is a
            // chance that none of them fit, in which case the caller would report those errors
            errsKeep.merge();
            }
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
            MethodStructure         method,
            List<Expression>        listExprArgs,
            Map<String, Expression> mapNamedExpr,
            ErrorListener           errs)
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
    protected MethodConstant chooseBest(Set<MethodConstant> setMethods, TypeConstant typeTarget,
            ErrorListener errs)
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
                boolean fOldBetter = sigMethod.isSubstitutableFor(sigBest, typeTarget);
                boolean fNewBetter = sigBest.isSubstitutableFor(sigMethod, typeTarget);
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
        TypeConstant[] atypeMethodReturn = sigMethod.getRawReturns();
        int            cMethodReturns    = atypeMethodReturn.length;
        int            cReturns          = atypeReturn.length;
        TypeFit        fit               = TypeFit.Fit;

        if (cMethodReturns < cReturns)
            {
            // we allow such an "asymmetrical" call in just one case:
            // - a "void" method return is allowed to be assigned to an empty Tuple
            //    void f() {...}
            //    Tuple v = f();
            if (fCall && cMethodReturns == 0 && cReturns == 1
                    && atypeReturn[0].isTuple()
                    && atypeReturn[0].getParamsCount() == 0)
                {
                return TypeFit.Pack;
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.INCOMPATIBLE_RETURN_COUNT,
                        sigMethod.getName(), String.valueOf(cReturns), String.valueOf(cMethodReturns));
                return TypeFit.NoFit;
                }
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
                            sigMethod.getName(), typeReturn.getValueString(), typeMethodReturn.getValueString());
                    return TypeFit.NoFit;
                    }
                }
            }
        return fit;
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
            Map.Entry entry = iter.next();
            Object    value = entry.getValue();
            if (value == null)
                {
                iter.remove();
                }
            else if (value instanceof Map)
                {
                if (((Map) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Collection)
                {
                if (((Collection) value).isEmpty())
                    {
                    iter.remove();
                    }
                }
            else if (value instanceof Object[])
                {
                if (((Object[]) value).length == 0)
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
            if (value instanceof Map)
                {
                Map kids = (Map) value;
                cKids = kids.size();
                iterK = kids.entrySet().iterator();
                }
            else if (value instanceof Collection)
                {
                Collection kids = (Collection) value;
                cKids = kids.size();
                iterK = kids.iterator();
                }
            else if (value instanceof Object[])
                {
                Object[] kids = (Object[]) value;
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

                if (kid instanceof AstNode)
                    {
                    if (fFirstK)
                        {
                        out.println();
                        }
                    ((AstNode) kid).dump(out, sIndent1, sIndent2);
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
                        out.println(sKid);
                        }
                    else
                        {
                        if (fFirstK)
                            {
                            out.println();
                            }
                        sKid = indentLines(sKid, sIndent2 + "  ");
                        sKid = sIndent1 + "- " + sKid.substring(sIndent1.length() + 2);
                        out.println(sKid);
                        }
                    }
                }
            }
        }

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
        return list instanceof ArrayList
                ? (ArrayList<T>) list
                : new ArrayList<T>(list);
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
         * Construct a ChildIterator that will iterate all of the children that are held in the
         * specified fields, which are either AstNodes themselves, or are container types thereof.
         *
         * @param fields  an array of fields of the AstNode
         */
        protected ChildIteratorImpl(Field[] fields)
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
                if (value instanceof AstNode)
                    {
                    return (AstNode) value;
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
            if (value instanceof Iterator && ((Iterator) value).hasNext())
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
                    if (next instanceof List)
                        {
                        List list = (List) next;
                        if (!list.isEmpty())
                            {
                            value = list.listIterator();
                            return true;
                            }
                        }
                    else if (next instanceof Collection)
                        {
                        Collection coll = (Collection) next;
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

                if (value instanceof Iterator)
                    {
                    // tell the underlying iterator to remove the value
                    ((Iterator) value).remove();
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

                if (value instanceof ListIterator)
                    {
                    ((ListIterator) value).set(newChild);
                    return;
                    }
                }

            throw new IllegalStateException();
            }

        private static final int NOT_PREP = 0;
        private static final int HAS_NEXT = 1;
        private static final int HAS_PREV = 2;

        private Field[] fields;
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
