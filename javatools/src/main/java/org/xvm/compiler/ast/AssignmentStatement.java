package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Expression.Assignable;
import org.xvm.compiler.ast.Expression.TypeFit;

import org.xvm.util.Severity;


/**
 * An assignment statement specifies an l-value, an assignment operator, and an r-value.
 *
 * Additionally, this can represent the assignment portion of a "conditional declaration".
 */
public class AssignmentStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssignmentStatement(AstNode lvalue, Token op, Expression rvalue)
        {
        this(lvalue, op, rvalue, true);
        }

    public AssignmentStatement(AstNode lvalue, Token op, Expression rvalue, boolean standalone)
        {
        this.lvalue = lvalue;
        this.op     = op;
        this.rvalue = rvalue;
        this.term   = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the AssignmentStatement has at least one L-Value that declares a new
     *         variable
     */
    public boolean hasDeclarations()
        {
        if (m_decls != null)
            {
            return m_decls.length > 0;
            }

        if (lvalue instanceof VariableDeclarationStatement)
            {
            return true;
            }

        if (lvalue instanceof MultipleLValueStatement)
            {
            for (AstNode LVal : ((MultipleLValueStatement) lvalue).LVals)
                {
                if (LVal instanceof VariableDeclarationStatement)
                    {
                    return true;
                    }
                }
            }

        m_decls = VariableDeclarationStatement.NONE;
        return false;
        }

    /**
     * @return all of the VariableDeclarationStatements that are used as L-Values by this statement
     */
    public VariableDeclarationStatement[] getDeclarations()
        {
        VariableDeclarationStatement[] aDecls = m_decls;
        if (aDecls == null)
            {
            AstNode LVal = lvalue;
            if (LVal instanceof VariableDeclarationStatement)
                {
                aDecls = new VariableDeclarationStatement[] {(VariableDeclarationStatement) LVal};
                }
            else if (LVal instanceof MultipleLValueStatement)
                {
                ArrayList<VariableDeclarationStatement> list = new ArrayList<>();
                for (AstNode node : ((MultipleLValueStatement) LVal).LVals)
                    {
                    if (node instanceof VariableDeclarationStatement)
                        {
                        list.add((VariableDeclarationStatement) node);
                        }
                    }
                aDecls = list.isEmpty()
                        ? VariableDeclarationStatement.NONE
                        : list.toArray(VariableDeclarationStatement.NONE);
                }
            else
                {
                aDecls = VariableDeclarationStatement.NONE;
                }

            m_decls = aDecls;
            }

        return aDecls;
        }

    /**
     * For each explicit variable declaration that provides an L-Value for this AssignmentStatement,
     * separate the VariableDeclarationStatement from this AssignmentStatement such that the
     * AssignmentStatement will no longer cause the variable to be declared, and instead will just
     * use the variable declared by the statement. (It becomes the responsibility of the caller
     * of this method to ensure that the VariableDeclarationStatement is processed before this
     * AssignmentStatement.)
     *
     * @return all of the VariableDeclarationStatements that are used as L-Values by this statement
     */
    public VariableDeclarationStatement[] takeDeclarations()
        {
        if (!hasDeclarations())
            {
            return VariableDeclarationStatement.NONE;
            }

        VariableDeclarationStatement[] aDecls = m_decls;

        AstNode LVal = lvalue;
        if (LVal instanceof VariableDeclarationStatement)
            {
            VariableDeclarationStatement stmt = (VariableDeclarationStatement) LVal;
            lvalue = new NameExpression(this, stmt.getNameToken(), stmt.getRegister());
            if (aDecls == null)
                {
                aDecls = new VariableDeclarationStatement[] {stmt};
                }
            }
        else
            {
            List<AstNode> LVals = ((MultipleLValueStatement) LVal).LVals;
            List<VariableDeclarationStatement> listDecls = aDecls == null ? new ArrayList<>() : null;
            for (int i = 0, c = LVals.size(); i < c; ++i)
                {
                AstNode node = LVals.get(i);
                if (node instanceof VariableDeclarationStatement)
                    {
                    VariableDeclarationStatement stmt = (VariableDeclarationStatement) node;
                    LVals.set(i, new NameExpression(this, stmt.getNameToken(), stmt.getRegister()));
                    if (listDecls != null)
                        {
                        listDecls.add(stmt);
                        }
                    }
                }
            if (aDecls == null)
                {
                aDecls = listDecls.toArray(VariableDeclarationStatement.NONE);
                }
            }

        m_decls = VariableDeclarationStatement.NONE;
        return aDecls;
        }

    /**
     * @return true iff this assignment statement is a for-each-condition
     */
    public boolean isForEachCondition()
        {
        return op.getId() == Id.COLON;
        }

    /**
     * @return true iff this assignment statement is an if-condition or for-each-condition
     */
    public boolean isConditional()
        {
        switch (op.getId())
            {
            case COND_ASN:
            case COND_NN_ASN:
                AstNode parent = getParent();
                return parent instanceof IfStatement
                    || parent instanceof WhileStatement
                    || parent instanceof ForStatement && ((ForStatement) parent).findCondition(this) >= 0
                    || parent instanceof AssertStatement;

            case COLON:
                return true;

            default:
                return false;
            }
        }

    /**
     * @return true iff the assignment statement uses the "=" operator
     */
    public Category getCategory()
        {
        switch (op.getId())
            {
            case ASN:
                return Category.Assign;

            case ADD_ASN:
            case SUB_ASN:
            case MUL_ASN:
            case DIV_ASN:
            case MOD_ASN:
            case SHL_ASN:
            case SHR_ASN:
            case USHR_ASN:
            case BIT_AND_ASN:
            case BIT_OR_ASN:
            case BIT_XOR_ASN:
                return Category.InPlace;

            case COND_AND_ASN:
            case COND_OR_ASN:
            case COND_ELSE_ASN:
                return Category.CondLeft;

            case COND_ASN:
            case COND_NN_ASN:
            case COLON:
                return Category.CondRight;

            default:
                throw new IllegalStateException("op=" + op);
            }
        }

    /**
     * @return the single-use register that the Boolean condition result is stored in
     */
    public Register getConditionRegister()
        {
        Register reg = m_regCond;
        if (reg == null)
            {
            switch (op.getId())
                {
                case COND_ASN:
                case COND_NN_ASN:
                case COLON:
                    m_regCond = reg = new Register(pool().typeBoolean(), Op.A_STACK);
                    break;

                default:
                    throw new IllegalStateException("op=\"" + op.getValueText() + '\"');
                }
            }

        return reg;
        }

    /**
     * @return the LValue that the RValue will be assigned to
     */
    public AstNode getLValue()
        {
        return lvalue;
        }

    /**
     * @return the assignment operator token
     */
    public Token getOp()
        {
        return op;
        }

    /**
     * @return the RValue expression that will be assigned to the LValue
     */
    public Expression getRValue()
        {
        return rvalue;
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        // there are a few reasons to disallow short-circuiting, such as when we have a declaration
        // with an assignment outside of a conditional
        // if (String s := a?.b())  // ok, conditional inside short-able statement
        // s = a?.b();              // ok, assignment with no declaration
        // String s = a?.b();       // error: pointless, thus unlikely what the dev wanted
        AstNode parent = getParent();
        if (parent instanceof ConditionalStatement)
            {
            return parent.allowsShortCircuit(this);
            }

        return !hasDeclarations();
        }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        return getParent() instanceof ConditionalStatement
                ? getParent().ensureShortCircuitLabel(nodeOrigin, ctxOrigin)
                : super.ensureShortCircuitLabel(nodeOrigin, ctxOrigin);
        }

    @Override
    public long getStartPosition()
        {
        return lvalue.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return rvalue.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isLValueSyntax()
        {
        return true;
        }

    @Override
    public Expression getLValueExpression()
        {
        return lvalueExpr;
        }

    @Override
    protected boolean isRValue(Expression exprChild)
        {
        return m_fSuppressLValue || exprChild != lvalue;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean    fValid       = true;
        AstNode    nodeLeft     = lvalue;
        Expression exprLeftCopy = null;
        boolean    fConditional = isConditional();

        switch (getCategory())
            {
            case CondLeft:
            case InPlace:
                // we'll create a fake R-value expression later to handle all of the duties of
                // checking definite assignment, types, etc., so we need a pristine copy of the
                // L-value expression to use for that purpose
                exprLeftCopy = (Expression) nodeLeft.getLValueExpression().clone();
            }

        ConstantPool pool      = pool();
        Context      ctxRValue = ctx;
        Context      ctxLValue = ctx;

        // a LValue represented by a statement indicates one or more variable declarations
        if (nodeLeft instanceof Statement)
            {
            assert nodeLeft instanceof VariableDeclarationStatement ||
                   nodeLeft instanceof MultipleLValueStatement;

            LValueContext ctxLV = new LValueContext(ctx);

            errs = errs.branch();

            Statement lvalueOld = (Statement) nodeLeft;
            Statement lvalueNew = lvalueOld.validate(ctxLV, errs);

            if (lvalueNew == null)
                {
                fValid = false;
                }
            else
                {
                lvalue = nodeLeft = lvalueNew;
                }

            if (errs.hasSeriousErrors())
                {
                fValid = false;
                }
            errs = errs.merge();

            // the validation of the VariableDeclarationStatement or MultipleLValueStatement above
            // only dealt with the "type" portion of the statement; only now we are entering the
            // l-value validation phase
            ctxLValue = ctxLV.enterLValue();
            }

        // regardless of whether the LValue is a statement or expression, all L-Values must be able
        // to provide an expression as a representative form
        Expression exprLeft = nodeLeft.getLValueExpression();
        if (!exprLeft.isValidated())
            {
            // the type of l-value may have been narrowed in the current context, so let's try to
            // extract it and test the r-value with it; to prevent the lvalue from dropping that
            // narrowed information, we temporarily need to forget it's an lvalue
            TypeConstant[] atypeLeft;
            m_fSuppressLValue = true;
            try
                {
                atypeLeft = exprLeft.getImplicitTypes(ctxLValue);
                }
            finally
                {
                m_fSuppressLValue = false;
                }

            int cLeft = atypeLeft.length;
            if (cLeft > 0)
                {
                TypeConstant[] atypeTest = atypeLeft;
                if (op.getId() == Id.COND_NN_ASN)
                    {
                    atypeTest = new TypeConstant[] {atypeLeft[0].ensureNullable()};
                    }
                else if (fConditional)
                    {
                    atypeTest = new TypeConstant[cLeft + 1];
                    atypeTest[0] = pool.typeBoolean();
                    System.arraycopy(atypeLeft, 0, atypeTest, 1, cLeft);
                    }

                // allow the r-value to resolve names based on the l-value type's
                // contributions
                Context ctxInfer = ctxRValue.enterInferring(atypeLeft[0]);

                TypeFit fit = rvalue.testFitMulti(ctxInfer, atypeTest, null);

                if (!fit.isFit() && cLeft > 1)
                    {
                    Expression exprUnpack = new UnpackExpression(rvalue, null);

                    fit = exprUnpack.testFitMulti(ctxInfer, atypeTest, null);
                    if (fit.isFit())
                        {
                        rvalue = exprUnpack;
                        }
                    }

                if (!fit.isFit())
                    {
                    // that didn't work; use the regular path
                    atypeLeft = TypeConstant.NO_TYPES;
                    }
                ctxInfer.discard();
                }

            Expression exprNew = exprLeft.validateMulti(ctxLValue, atypeLeft, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                if (exprLeft == nodeLeft)
                    {
                    lvalue = nodeLeft = exprNew;
                    }
                exprLeft = exprNew;
                }
            }
        lvalueExpr = exprLeft;
        exprLeft.requireAssignable(ctxLValue, errs);

        Expression     exprRight    = rvalue;
        Expression     exprRightNew = null;
        TypeConstant[] atypeRight   = null;     // used only to update the left type info
        switch (getCategory())
            {
            case Assign:
                if (exprLeft.isSingle())
                    {
                    // LVal = RVal (or some other assignment operator, not ':')
                    TypeConstant typeLeft = exprLeft.getType();
                    boolean      fInfer   = typeLeft != null;
                    if (fInfer)
                        {
                        // allow the r-value to resolve names based on the l-value type's
                        // contributions
                        ctxRValue = ctxRValue.enterInferring(typeLeft);
                        }

                    if (exprLeft instanceof NameExpression &&
                            ((NameExpression) exprLeft).isDynamicVar())
                        {
                        // test for a future assignment first
                        TypeConstant typeFuture = pool.ensureFutureVar(typeLeft);
                        if (exprRight.testFit(ctxRValue, typeFuture, null).isFit())
                            {
                            typeLeft = typeFuture;
                            }
                        }
                    exprRightNew = exprRight.validate(ctxRValue, typeLeft, errs);

                    if (fInfer)
                        {
                        ctxRValue = ctxRValue.exit();
                        }

                    if (exprRight instanceof InvocationExpression &&
                            ((InvocationExpression) exprRight).isAsync() &&
                        exprLeft instanceof NameExpression)
                        {
                        // auto-convert to a @Future register, e.g.
                        //      Int i = svc.f^();
                        // into
                        //      @Future Int i = svc.f^();
                        Argument argLeft = ctxLValue.getVar(((NameExpression) exprLeft).getName());
                        if (argLeft instanceof Register)
                            {
                            Register     regLeft = (Register) argLeft;
                            TypeConstant typeRef = regLeft.ensureRegType(false);

                            if (!typeRef.containsAnnotation(pool.clzFuture()))
                                {
                                regLeft.specifyRegType(pool.ensureAnnotatedTypeConstant(
                                        typeRef, pool.ensureAnnotation(pool.clzFuture())));
                                }
                            }
                        }
                    }
                else
                    {
                    // (LVal0, LVal1, ..., LValN) = RVal
                    exprRightNew = exprRight.validateMulti(ctxRValue, exprLeft.getTypes(), errs);
                    }

                if (fValid)
                    {
                    merge(ctxRValue, ctxLValue);
                    }

                // prevent unnecessary errors and mark an unconditional assignment even if the validation failed
                exprLeft.markAssignment(ctxRValue, exprRightNew != null && exprRightNew.isConditionalResult(), errs);
                if (exprRightNew != null)
                    {
                    atypeRight = exprRightNew.getTypes();
                    }
                break;

            case CondRight:
                {
                if (op.getId() == Id.COND_NN_ASN)
                    {
                    // verify only one value, and that it is nullable
                    if (!exprLeft.isSingle())
                        {
                        lvalueExpr.log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, 1, exprLeft.getValueCount());
                        }

                    // the right side is (must be) a nullable form of the type on the left
                    TypeConstant typeReq = exprLeft.getType();
                    if (typeReq != null)
                        {
                        typeReq = typeReq.ensureNullable();
                        }

                    exprRightNew = exprRight.validate(ctxRValue, typeReq, errs);

                    if (fValid)
                        {
                        merge(ctxRValue, ctxLValue);
                        }

                    exprLeft.markAssignment(ctxRValue,
                            !fConditional && exprRight != null && exprRight.isConditionalResult(), errs);
                    if (exprRightNew != null)
                        {
                        atypeRight = exprRightNew.getTypes();
                        if (atypeRight.length == 1)
                            {
                            TypeConstant typeRight = atypeRight[0];
                            if (typeRight.isNullable())
                                {
                                atypeRight = new TypeConstant[]{typeRight.removeNullable()};
                                }
                            else
                                {
                                // REVIEW CP: make this an error instead?
                                exprRight.log(errs, Severity.WARNING, Compiler.EXPRESSION_NOT_NULLABLE,
                                    typeRight.getValueString());
                                }
                            }
                        }
                    }
                else
                    {
                    // (LVal : RVal) or (LVal0, LVal1, ..., LValN : RVal)
                    TypeConstant[] atypeLVals = exprLeft.getTypes();
                    int            cLVals     = atypeLVals.length;
                    int            cReq       = cLVals + 1;
                    TypeConstant[] atypeReq   = new TypeConstant[cReq];
                    atypeReq[0] = pool().typeBoolean();
                    System.arraycopy(atypeLVals, 0, atypeReq, 1, cLVals);

                    exprRightNew = exprRight.validateMulti(ctxRValue, atypeReq, errs);

                    if (fValid)
                        {
                        merge(ctxRValue, ctxLValue);
                        }

                    // conditional expressions can update the LVal type from the RVal type, but the
                    // initial boolean is discarded
                    if (exprRightNew != null)
                        {
                        exprLeft.markAssignment(ctxRValue,
                            fConditional && exprRight != null && exprRight.isConditionalResult(), errs);

                        if (fConditional)
                            {
                            TypeConstant[] atypeAll = exprRightNew.getTypes();
                            int            cTypes   = atypeAll.length - 1;
                            if (cTypes >= 1)
                                {
                                atypeRight = new TypeConstant[cTypes];
                                System.arraycopy(atypeAll, 1, atypeRight, 0, cTypes);
                                }
                            }
                        }
                    }

                // the LValues must NOT be declarations!!! (they wouldn't be assigned)
                if (hasDeclarations() && !fConditional)
                    {
                    log(errs, Severity.ERROR, Compiler.VAR_DECL_COND_ASN_ILLEGAL);
                    }
                break;
                }

            case CondLeft:
            case InPlace:
                {
                assert exprLeftCopy != null;

                TypeConstant typeLeft = exprLeft.getType();
                boolean      fInfer   = typeLeft != null;
                if (fInfer)
                    {
                    // allow the r-value to resolve names based on the l-value type's
                    // contributions
                    ctxRValue = ctxRValue.enterInferring(typeLeft);
                    }

                BiExpression exprFakeRValue    = createBiExpression(exprLeftCopy, op, exprRight);
                Expression   exprFakeRValueNew = exprFakeRValue.validate(ctxRValue, typeLeft, errs);
                if (exprFakeRValueNew instanceof BiExpression)
                    {
                    exprRightNew = ((BiExpression) exprFakeRValueNew).getExpression2();
                    if (getCategory() == Category.CondLeft)
                        {
                        atypeRight = exprRightNew.getTypes();
                        }
                    }
                else
                    {
                    // this could only happen after an error had been reported
                    assert errs.hasSeriousErrors();
                    fValid = false;
                    }

                if (fInfer)
                    {
                    ctxRValue = ctxRValue.exit();
                    }

                if (fValid)
                    {
                    merge(ctxRValue, ctxLValue);
                    }

                exprLeft.markAssignment(ctxRValue, false, errs);
                break;
                }
            }

        if (exprRightNew == null)
            {
            fValid = false;
            }
        else
            {
            rvalue = exprRightNew;

            if (atypeRight != null)
                {
                nodeLeft.updateLValueFromRValueTypes(ctxRValue, atypeRight);
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected void selectTraceableExpressions(Map<String, Expression> mapExprs)
        {
        assert op.getId() == Id.COND_ASN || op.getId() == Id.COND_NN_ASN;
        rvalue.selectTraceableExpressions(mapExprs);
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean      fCompletes = fReachable;
        ConstantPool pool       = pool();

        switch (getCategory())
            {
            case Assign:
                {
                // code gen optimization for the common case of a combined declaration and
                // constant assignment of a single value
                if (lvalueExpr.isSingle()
                        && lvalue instanceof VariableDeclarationStatement
                        && !((VariableDeclarationStatement) lvalue).hasRefAnnotations()
                        && rvalue.supportsCompactInit((VariableDeclarationStatement) lvalue))
                    {
                    assert lvalueExpr.isCompletable();
                    rvalue.generateCompactInit(ctx, code, (VariableDeclarationStatement) lvalue,  errs);
                    break;
                    }

                if (lvalue instanceof Statement)
                    {
                    fCompletes = ((Statement) lvalue).completes(ctx, fCompletes, code, errs);
                    }

                Assignable[] LVals = lvalueExpr.generateAssignables(ctx, code, errs);
                if (fCompletes &= lvalueExpr.isCompletable())
                    {
                    rvalue.generateAssignments(ctx, code, LVals, errs);
                    fCompletes &= rvalue.isCompletable();
                    }
                break;
                }

            case CondRight:
                {
                if (lvalue instanceof Statement)
                    {
                    fCompletes = ((Statement) lvalue).completes(ctx, fCompletes, code, errs);
                    }

                if (op.getId() == Id.COND_NN_ASN)
                    {
                    Argument arg = rvalue.generateArgument(ctx, code, false, false, errs);
                    fCompletes &= rvalue.isCompletable();

                    Label labelSkipAssign = new Label("?=Null");
                    code.add(new JumpNull(arg, labelSkipAssign));

                    Assignable LVal = lvalueExpr.generateAssignable(ctx, code, errs);
                    LVal.assign(arg, code, errs);

                    Register regCond = isConditional() ? getConditionRegister() : null;
                    if (regCond != null)
                        {
                        // assignment happened, so the conditional register should be True
                        code.add(new Move(pool.valTrue(), regCond));
                        code.add(new Jump(getEndLabel()));
                        }

                    code.add(labelSkipAssign);
                    if (regCond != null)
                        {
                        // assignment did NOT happen, so the conditional register should be False
                        code.add(new Move(pool.valFalse(), regCond));
                        }
                    }
                else
                    {
                    Assignable[] LVals    = lvalueExpr.generateAssignables(ctx, code, errs);
                    int          cLVals   = LVals.length;
                    int          cAll     = cLVals + 1;
                    Assignable[] LValsAll = new Assignable[cAll];
                    LValsAll[0] = isConditional()
                            ? lvalueExpr.new Assignable(getConditionRegister())
                            : lvalueExpr.new Assignable();  // stand-alone assign discards Boolean
                    System.arraycopy(LVals, 0, LValsAll, 1, cLVals);
                    if (fCompletes &= lvalueExpr.isCompletable())
                        {
                        rvalue.generateAssignments(ctx, code, LValsAll, errs);
                        fCompletes &= rvalue.isCompletable();
                        }
                    }
                break;
                }

            case CondLeft:
                {
                // "a &&= b" -> "if (a == true ) {a = b;}"
                // "a ||= b" -> "if (a == false) {a = b;}"
                // "a ?:= b" -> "if (a == null ) {a = b;}"
                Assignable LVal = lvalueExpr.generateAssignable(ctx, code, errs);
                if (fCompletes &= lvalueExpr.isCompletable())
                    {
                    Argument argLVal   = LVal.generateArgument(ctx, code, true, true, errs);
                    Label    labelSkip = new Label(op.getValueText() + " _skip_assign");
                    switch (op.getId())
                        {
                        case COND_AND_ASN:
                            code.add(new JumpFalse(argLVal, labelSkip));
                            break;

                        case COND_OR_ASN:
                            code.add(new JumpTrue(argLVal, labelSkip));
                            break;

                        case COND_ELSE_ASN:
                            code.add(new JumpNotNull(argLVal, labelSkip));
                            break;

                        default:
                            throw new IllegalStateException("op=" + op.getId().TEXT);
                        }
                    rvalue.generateAssignment(ctx, code, LVal, errs);
                    fCompletes &= rvalue.isCompletable();
                    code.add(labelSkip);
                    }
                break;
                }

            case InPlace:
                {
                Assignable LVal = lvalueExpr.generateAssignable(ctx, code, errs);
                if (fCompletes &= lvalueExpr.isCompletable())
                    {
                    Argument argRVal = rvalue.generateArgument(ctx, code, true, true, errs);
                    if (fCompletes &= rvalue.isCompletable())
                        {
                        LVal.assignInPlaceResult(op, argRVal, code, errs);
                        }
                    }
                break;
                }

            default:
                throw new IllegalStateException();
            }

        return fCompletes;
        }


    // ----- compiling helpers ---------------------------------------------------------------------

    /**
     * Create an R-Value expression that represents the "<b>{@code a op b}</b>" (no assignment)
     * portion of an "<b>{@code a op= b}</b>" (in-place assignment) statement.
     *
     * @param exprLeft
     * @param opIP
     * @param exprRight
     *
     * @return
     */
    private BiExpression createBiExpression(Expression exprLeft, Token opIP, Expression exprRight)
        {
        Token.Id idBi;
        switch (opIP.getId())
            {
            case ADD_ASN      : idBi = Id.ADD      ; break;
            case SUB_ASN      : idBi = Id.SUB      ; break;
            case MUL_ASN      : idBi = Id.MUL      ; break;
            case DIV_ASN      : idBi = Id.DIV      ; break;
            case MOD_ASN      : idBi = Id.MOD      ; break;
            case SHL_ASN      : idBi = Id.SHL      ; break;
            case SHR_ASN      : idBi = Id.SHR      ; break;
            case USHR_ASN     : idBi = Id.USHR     ; break;
            case BIT_AND_ASN  : idBi = Id.BIT_AND  ; break;
            case BIT_OR_ASN   : idBi = Id.BIT_OR   ; break;
            case BIT_XOR_ASN  : idBi = Id.BIT_XOR  ; break;
            case COND_AND_ASN : idBi = Id.COND_AND ; break;
            case COND_OR_ASN  : idBi = Id.COND_OR  ; break;
            case COND_ELSE_ASN: idBi = Id.COND_ELSE; break;

            case ASN:
            case COLON:
            case COND_ASN:
            case COND_NN_ASN:
            default:
                throw new IllegalStateException("op=" + opIP.getId().TEXT);
            }

        Token        opBi = new Token(opIP.getStartPosition(), opIP.getEndPosition(), idBi);
        BiExpression exprResult;
        switch (idBi)
            {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case SHL:
            case SHR:
            case USHR:
            case BIT_AND:
            case BIT_OR:
            case BIT_XOR:
                exprResult = new RelOpExpression(exprLeft, opBi, exprRight);
                break;

            case COND_AND:
            case COND_OR:
                exprResult = new CondOpExpression(exprLeft, opBi, exprRight);
                break;

            case COND_ELSE:
                exprResult = new ElvisExpression(exprLeft, opBi, exprRight);
                break;

            default:
                throw new IllegalStateException("op=" + opBi.getId().TEXT);
            }

        exprResult.setParent(this);
        return exprResult;
        }

    private void merge(Context ctx, Context ctxLvalue)
        {
        if (ctx != ctxLvalue)
            {
            assert ctxLvalue.getOuterContext() == ctx;
            ctxLvalue.exit();
            }
        }

    /**
     * A branching context that temporary holds the declarations made by the LValue.
     */
    protected static class LValueContext
            extends Context
        {
        protected LValueContext(Context ctxOuter)
            {
            super(ctxOuter, false);
            }

        @Override
        protected Argument resolveRegisterType(Branch branch, Register reg)
            {
            // LValue relies only on the declared type; any inference should be reset
            return m_fInLValue ? reg.getOriginalRegister() : reg;
            }

        @Override
        public Context exit()
            {
            Context ctxOuter = getOuterContext();

            // copy variable assignment information from this scope to outer scope
            for (Map.Entry<String, Assignment> entry : getDefiniteAssignments().entrySet())
                {
                String sName = entry.getKey();

                if (isVarDeclaredInThisScope(sName))
                    {
                    ctxOuter.setVarAssignment(sName, entry.getValue());
                    }
                }

            for (Map.Entry<String, Argument> entry : getNameMap().entrySet())
                {
                String sName = entry.getKey();

                if (isVarDeclaredInThisScope(sName))
                    {
                    ctxOuter.replaceArgument(sName, Branch.Always, entry.getValue());
                    }
                }
            return super.exit();
            }

        /**
         * Mark this context as entering the l-value validation phase.
         */
        protected LValueContext enterLValue()
            {
            m_fInLValue = true;
            return this;
            }

        /**
         * Indicates whether the context has entered the l-value validation phase.
         */
        private boolean m_fInLValue;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(lvalue)
          .append(' ')
          .append(op.getId().TEXT)
          .append(' ')
          .append(rvalue);

        if (term)
            {
            sb.append(';');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    public enum Category
        {
        /**
         * a = b
         */
        Assign,
        /**
         * a += b, a &= b, etc.
         */
        InPlace,
        /**
         * a &&= b, a ||= b, a ?:= b
         */
        CondLeft,
        /**
         * a := b, a ?= b
         * if (a := b), if (a ?= b) ...
         * for (a : b) ...
         */
        CondRight,
        }

    protected AstNode    lvalue;
    protected Expression lvalueExpr;
    protected Token      op;
    protected Expression rvalue;
    protected boolean    term;

    private transient VariableDeclarationStatement[] m_decls;
    private transient Register                       m_regCond;
    private transient boolean                        m_fSuppressLValue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssignmentStatement.class, "lvalue", "lvalueExpr", "rvalue");
    }
