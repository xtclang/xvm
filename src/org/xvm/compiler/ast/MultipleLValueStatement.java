package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;


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
        assert LVals.stream().allMatch(node -> node.isLValueSyntax());

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


    // ----- LValue methdos ------------------------------------------------------------------------

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
    public void updateLValueFromRValueType(TypeConstant type)
        {
        assert type.isParamsSpecified();
        TypeConstant[] aTypes = type.getParamTypesArray();
        for (int i = 0, c = Math.max(aTypes.length, LVals.size()); i < c; ++i)
            {
            LVals.get(i).updateLValueFromRValueType(aTypes[i]);
            }
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        assert findChild(exprChild) >= 0;
        return true;
        }

    @Override
    protected Label getShortCircuitLabel(Expression exprChild)
        {
        int iPos = findChild(exprChild);
        if (iPos < 0)
            {
            throw new IllegalStateException("unknown child: " + exprChild);
            }

        return ensureShortCircuitLabel(iPos);
        }

    /**
     * Find the specified node within the list of child nodes.
     *
     * @param node  the child node to find
     *
     * @return the position in the list of the child, or -1 if the child could not be found
     */
    int findChild(AstNode node)
        {
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
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        for (int i = 0, c = LVals.size(); i < c; ++i)
            {
            AstNode nodeOld = LVals.get(i);
            AstNode nodeNew = nodeOld instanceof Statement
                    ? ((Statement) nodeOld).validate(ctx, errs)
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
            // REVIEW - do we need to create a temp var here for all non-statements? (non-decls) ... or just for short-circuitable expressions?

            AstNode node = LVals.get(i);
            if (node instanceof Statement)
                {
                fCompletes = ((Statement) node).emit(ctx, fCompletes, code, errs);
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
        public void updateLValueFromRValueType(TypeConstant type)
            {
            getParent().updateLValueFromRValueType(type);
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
            Expression[]   aExprs = ensureExpressions();
            int            cTypes = aExprs.length;
            TypeConstant[] aTypes = new TypeConstant[cTypes];
            for (int i = 0; i < cTypes; ++i)
                {
                aTypes[i] = aExprs[i].getImplicitType(ctx);
                }
            return aTypes;
            }

        @Override
        public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
            {
            int          cReq   = atypeRequired == null ? 0 : atypeRequired.length;
            Expression[] aExprs = ensureExpressions();
            int          cExprs = aExprs.length;
            if (cReq > cExprs)
                {
                return TypeFit.NoFit;
                }

            TypeFit fit = TypeFit.Fit;
            for (int i = 0; i < cReq; ++i)
                {
                TypeFit fitSingle = aExprs[i].testFit(ctx, atypeRequired[i]);
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
            Expression exprResult = this;

            Expression[] aExprs = ensureExpressions();
            int          cExprs = aExprs.length;
            int          cReq   = atypeRequired == null ? 0 : atypeRequired.length;
            for (int i = 0; i < cExprs; ++i)
                {
                Expression exprOld = aExprs[i];
                Expression exprNew = exprOld.validate(ctx, i < cReq ? atypeRequired[i] : null, errs);
                if (exprNew == null)
                    {
                    exprResult = null;
                    }
                else if (exprNew != exprOld)
                    {
                    aExprs[i] = exprNew;
                    }
                }

            return exprResult;
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
            Expression[] aExprs = ensureExpressions();
            int          cExprs = aExprs.length;
            Assignable[] aLVals = new Assignable[cExprs];
            for (int i = 0; i < cExprs; ++i)
                {
                // TODO check grounding labels? for each one, we need to pipe RValues into "the blackhole" or into
                // TODO the underlying assignable, which means that we'll need a temporary to hold the value

                aLVals[i] = aExprs[i].generateAssignable(ctx, code, errs);
                }
            return aLVals;
            }

        @Override
        public boolean isAssignable()
            {
            return super.isAssignable();
            }

        @Override
        public void requireAssignable(Context ctx, ErrorListener errs)
            {
            super.requireAssignable(ctx, errs);
            }

        @Override
        public boolean isAborting()
            {
            return super.isAborting();
            }

        @Override
        public boolean isShortCircuiting()
            {
            return super.isShortCircuiting();
            }

        @Override
        public boolean isConstant()
            {
            return super.isConstant();
            }

        @Override
        protected boolean allowsShortCircuit(Expression exprChild)
            {
            if (findChild(exprChild) < 0)
                {
                throw new IllegalStateException("unknown child: " + exprChild);
                }

            return true;
            }

        @Override
        protected Label getShortCircuitLabel(Expression exprChild)
            {
            int iPos = findChild(exprChild);
            if (iPos < 0)
                {
                throw new IllegalStateException("unknown child: " + exprChild);
                }

            return MultipleLValueStatement.this.ensureShortCircuitLabel(iPos);
            }

        @Override
        protected Expression replaceThisWith(Expression that)
            {
            throw new IllegalStateException();
            }

        /**
         * @return an array of underlying LValue expressions
         */
        Expression[] ensureExpressions()
            {
            Expression[] aExprs = this.exprs;
            if (aExprs == null)
                {
                List<AstNode> LVals  = MultipleLValueStatement.this.LVals;
                int           cExprs = LVals.size();
                aExprs = new Expression[cExprs];
                for (int i = 0; i < cExprs; ++i)
                    {
                    aExprs[i] = LVals.get(i).getLValueExpression();
                    }
                this.exprs = aExprs;
                }
            return aExprs;
            }

        /**
         * Find the specified expression within the array of child expressions.
         *
         * @param expr  the expression
         *
         * @return the position in the array of the child, or -1 if the child could not be found
         */
        int findChild(Expression expr)
            {
            Expression[] aExprs = ensureExpressions();
            int          cExprs = aExprs.length;
            for (int i = 0; i < cExprs; ++i)
                {
                if (expr == aExprs[i])
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

        protected Expression[] exprs;
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
