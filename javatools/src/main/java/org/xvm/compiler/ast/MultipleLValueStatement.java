package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler.Stage;

import org.xvm.compiler.ast.Context.Branch;


/**
 * This represents multiple variable declarations in a list.
 */
public class MultipleLValueStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct MultipleLValueStatement.
     *
     * @param LVals a list of statements and expressions representing LValues
     */
    public MultipleLValueStatement(List<AstNode> LVals)
        {
        assert LVals.stream().allMatch(AstNode::isLValueSyntax);

        this.LVals = LVals;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return LVals.get(0).getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return LVals.get(LVals.size() - 1).getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return STMT_FIELDS;
        }

    /**
     * @return the types being assigned to
     */
    public TypeConstant[] getTypes()
        {
        List<AstNode>  listLVals = LVals;
        int            cTypes    = listLVals.size();
        TypeConstant[] aTypes    = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; ++i)
            {
            AstNode nodeLVal = listLVals.get(i);
            aTypes[i] = nodeLVal instanceof VariableDeclarationStatement stmtDecl
                    ? stmtDecl.getType()
                    : nodeLVal.getLValueExpression().getType();
            }
        return aTypes;
        }


    // ----- LValue methods ------------------------------------------------------------------------

    @Override
    public boolean isLValueSyntax()
        {
        return true;
        }

    @Override
    public Expression getLValueExpression()
        {
        if (expr == null)
            {
            expr = new MultipleLValueExpression();
            }
        return expr;
        }

    @Override
    public void updateLValueFromRValueTypes(Context ctx, Branch branch, TypeConstant[] aTypes)
        {
        assert aTypes != null && aTypes.length >= 1;

        List<AstNode>  listLVals = LVals;
        for (int i = 0, c = Math.min(aTypes.length, listLVals.size()); i < c; ++i)
            {
            listLVals.get(i).updateLValueFromRValueTypes(ctx, branch, new TypeConstant[] {aTypes[i]});
            }
        }

    @Override
    protected boolean isRValue(Expression exprChild)
        {
        return false;
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        assert indexOfChild(nodeChild) >= 0;
        return true;
        }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        AstNode nodeChild = findChild(nodeOrigin);
        int     iPos      = indexOfChild(nodeChild);
        if (iPos < 0)
            {
            throw new IllegalStateException("unknown child: " + nodeChild);
            }

        return ensureShortCircuitLabel(iPos); // TODO CP need to pass origin info!!!
        }

    /**
     * Find the specified node within the list of child nodes.
     *
     * @param node  the child node to find
     *
     * @return the position in the list of the child, or -1 if the child could not be found
     */
    int indexOfChild(AstNode node)
        {
        assert node != null;

        List<AstNode> list = LVals;
        for (int i = 0, cNodes = list.size(); i < cNodes; ++i)
            {
            if (node == list.get(i))
                {
                return i;
                }
            }
        return -1;
        }

    /**
     * @return the specified label, iff it already exists
     */
    Label peekShortCircuitLabel(int i)
        {
        return aGroundLabels == null ? null : aGroundLabels[i];
        }

    /**
     * @return the specified label, creating it if necessary
     */
    Label ensureShortCircuitLabel(int i)
        {
        Label[] aLabels = aGroundLabels;
        if (aLabels == null)
            {
            aGroundLabels = aLabels = new Label[LVals.size()];
            }

        Label label = aLabels[i];
        if (label == null)
            {
            aLabels[i] = label = new Label("ground_" + i);
            }

        return label;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        for (int i = 0, c = LVals.size(); i < c; ++i)
            {
            AstNode nodeOld = LVals.get(i);
            AstNode nodeNew = nodeOld instanceof Statement stmt
                    ? stmt.validate(ctx, errs)
                    : ((Expression) nodeOld).validate(ctx, null, errs);
            if (nodeNew != null && nodeNew != nodeOld)
                {
                LVals.set(i, nodeNew);
                }
            }
        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        List<AstNode> LVals = this.LVals;
        for (int i = 0, c = LVals.size(); i < c; ++i)
            {
            // REVIEW - how much of the short-circuitable LValue "a?[b]" should be handled here?
            // REVIEW - do we need to create a temp var here for all non-statements? (non-decls) ...
            //          or just for short-circuitable expressions?

            AstNode node = LVals.get(i);
            if (node instanceof Statement stmt)
                {
                fCompletes = stmt.completes(ctx, fCompletes, code, errs);
                }

            Label labelGround = peekShortCircuitLabel(i);
            if (labelGround != null)
                {
                code.add(labelGround);
                }
            }

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        boolean first = true;
        for (AstNode node : LVals)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }

            sb.append(node);
            }

        sb.append(')');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- inner class: MultipleLValueExpression -------------------------------------------------

    /**
     * This is a "fake" expression that delegates to the underlying AstNode LValue expressions that
     * are represented by the MultipleLValueStatement.
     */
    protected class MultipleLValueExpression
            extends Expression
        {
        @Override
        public MultipleLValueStatement getParent()
            {
            return MultipleLValueStatement.this;
            }

        @Override
        public long getStartPosition()
            {
            return getParent().getStartPosition();
            }

        @Override
        public long getEndPosition()
            {
            return getParent().getEndPosition();
            }

        @Override
        protected Field[] getChildFields()
            {
            return EXPR_FIELDS;
            }

        @Override
        public Stage getStage()
            {
            return getParent().getStage();
            }

        @Override
        protected void setStage(Stage stage)
            {
            getParent().setStage(stage);
            }

        @Override
        public boolean isLValueSyntax()
            {
            return true;
            }

        @Override
        public Expression getLValueExpression()
            {
            return this;
            }

        @Override
        public void updateLValueFromRValueTypes(Context ctx, Branch branch, TypeConstant[] aTypes)
            {
            getParent().updateLValueFromRValueTypes(ctx, branch, aTypes);
            }

        @Override
        protected boolean usesSuper()
            {
            return getParent().usesSuper();
            }

        @Override
        protected boolean hasSingleValueImpl()
            {
            return false;
            }

        @Override
        protected boolean hasMultiValueImpl()
            {
            return true;
            }

        @Override
        public TypeConstant[] getImplicitTypes(Context ctx)
            {
            List<Expression> listExprs = ensureExpressions();
            int              cTypes    = listExprs.size();
            TypeConstant[]   aTypes    = new TypeConstant[cTypes];
            for (int i = 0; i < cTypes; ++i)
                {
                TypeConstant type = listExprs.get(i).getImplicitType(ctx);
                if (type == null)
                    {
                    // the type cannot be determined, so we have to either assume "any type" aka
                    // "Object", or return TypeConstant.NO_TYPES to indicate failure
                    type = pool().typeObject();
                    }
                aTypes[i] = type;
                }
            return aTypes;
            }

        @Override
        public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                    ErrorListener errs)
            {
            int              cReq      = atypeRequired == null ? 0 : atypeRequired.length;
            List<Expression> listExprs = ensureExpressions();
            int              cExprs    = listExprs.size();
            if (cReq > cExprs)
                {
                return TypeFit.NoFit;
                }

            TypeFit fit = TypeFit.Fit;
            for (int i = 0; i < cReq; ++i)
                {
                TypeFit fitSingle = listExprs.get(i).testFit(ctx, atypeRequired[i], fExhaustive, errs);
                if (!fitSingle.isFit())
                    {
                    return TypeFit.NoFit;
                    }

                fit = fit.combineWith(fitSingle);
                }

            return fit;
            }

        @Override
        protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
            {
            TypeFit fit = TypeFit.Fit;

            List<Expression> listExprs   = ensureExpressions();
            int              cExprs      = listExprs.size();
            TypeConstant[]   atypeActual = new TypeConstant[cExprs];
            int              cRequired   = atypeRequired == null ? 0 : atypeRequired.length;
            for (int i = 0; i < cExprs; ++i)
                {
                Expression exprOld = listExprs.get(i);
                Expression exprNew = exprOld.validate(ctx, i < cRequired ? atypeRequired[i] : null, errs);
                if (exprNew == null)
                    {
                    atypeActual = null;
                    fit         = TypeFit.NoFit;
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        listExprs.set(i, exprNew);
                        }
                    if (atypeActual != null)
                        {
                        atypeActual[i] = exprNew.getType();
                        }
                    fit = fit.combineWith(exprNew.getTypeFit());
                    }
                }

            return finishValidations(ctx, atypeRequired, atypeActual, fit, null, errs);
            }

        @Override
        public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
            {
            throw new IllegalStateException();
            }

        @Override
        public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
            {
            throw new IllegalStateException();
            }

        @Override
        public Assignable[] generateAssignables(Context ctx, Code code, ErrorListener errs)
            {
            List<Expression> listExprs = ensureExpressions();
            int              cExprs    = listExprs.size();
            Assignable[]     aLVals    = new Assignable[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                // TODO check grounding labels? for each one, we need to pipe RValues into "the blackhole" or into
                // TODO the underlying assignable, which means that we'll need a temporary to hold the value

                aLVals[i] = listExprs.get(i).generateAssignable(ctx, code, errs);
                }
            return aLVals;
            }

        @Override
        public boolean isAssignable(Context ctx)
            {
            return super.isAssignable(ctx);
            }

        @Override
        public void requireAssignable(Context ctx, ErrorListener errs)
            {
            for (Expression expr : ensureExpressions())
                {
                expr.requireAssignable(ctx, errs);
                }
            }

        @Override
        public void markAssignment(Context ctx, boolean fCond, ErrorListener errs)
            {
            for (Expression expr : ensureExpressions())
                {
                expr.markAssignment(ctx, fCond, errs);
                }
            }

        @Override
        public boolean isCompletable()
            {
            for (Expression expr : ensureExpressions())
                {
                if (!expr.isCompletable())
                    {
                    return false;
                    }
                }

            return true;
            }

        @Override
        public boolean isShortCircuiting()
            {
            for (Expression expr : ensureExpressions())
                {
                if (expr.isShortCircuiting())
                    {
                    return true;
                    }
                }

            return false;
            }

        @Override
        public boolean isConstant()
            {
            for (Expression expr : ensureExpressions())
                {
                if (!expr.isConstant())
                    {
                    return false;
                    }
                }

            return true;
            }

        @Override
        protected boolean allowsShortCircuit(AstNode nodeChild)
            {
            return nodeChild instanceof Expression exprChild && indexOfChild(exprChild) >= 0;
            }

        @Override
        protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
            {
            AstNode nodeChild = findChild(nodeOrigin);
            int     iPos      = nodeChild instanceof Expression exprChild ? indexOfChild(exprChild) : -1;
            if (iPos < 0)
                {
                throw new IllegalStateException("unknown child: " + nodeChild);
                }

            return MultipleLValueStatement.this.ensureShortCircuitLabel(iPos);
            }

        @Override
        protected Expression replaceThisWith(Expression that)
            {
            throw new IllegalStateException();
            }

        /**
         * @return a list of underlying LValue expressions
         */
        List<Expression> ensureExpressions()
            {
            List<Expression> listExprs = this.exprs;
            if (listExprs == null)
                {
                List<AstNode> LVals  = MultipleLValueStatement.this.LVals;
                int           cExprs = LVals.size();
                listExprs = new ArrayList<>(cExprs);
                for (int i = 0; i < cExprs; ++i)
                    {
                    listExprs.add(LVals.get(i).getLValueExpression());
                    }
                this.exprs = listExprs;
                }
            return listExprs;
            }

        /**
         * Find the specified expression within the array of child expressions.
         *
         * @param expr  the expression
         *
         * @return the position in the array of the child, or -1 if the child could not be found
         */
        int indexOfChild(Expression expr)
            {
            assert expr != null;

            List<Expression> listExprs = ensureExpressions();
            for (int i = 0, c = listExprs.size(); i < c; ++i)
                {
                if (expr == listExprs.get(i))
                    {
                    return i;
                    }
                }
            return -1;
            }

        @Override
        public String toString()
            {
            return getParent().toString();
            }

        protected List<Expression> exprs;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The list of LValue statements and expressions that make up this MultipleLValueStatement.
     */
    protected List<AstNode> LVals;

    /**
     * Grounding labels for LValue expressions that can short-circuit.
     */
    protected transient Label[] aGroundLabels;

    /**
     * Lazily instantiated expression that represents the multiple underlying LValue expressions.
     */
    protected transient MultipleLValueExpression expr;

    private static final Field[] STMT_FIELDS = fieldsForNames(MultipleLValueStatement.class, "LVals");
    private static final Field[] EXPR_FIELDS = fieldsForNames(MultipleLValueExpression.class, "exprs");
    }