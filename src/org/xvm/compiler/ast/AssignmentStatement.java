package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Var_IN;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Expression.Assignable;
import org.xvm.compiler.ast.Expression.TypeFit;


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
                        : list.toArray(new VariableDeclarationStatement[list.size()]);
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
                aDecls = listDecls.toArray(new VariableDeclarationStatement[listDecls.size()]);
                }
            }

        m_decls = VariableDeclarationStatement.NONE;
        return aDecls;
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

            case COLON:
                return Category.CondExpr;

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
            if (getCategory() != Category.CondExpr)
                {
                throw new IllegalStateException("op=\"" + op.getValueText() + '\"');
                }

            m_regCond = reg = new Register(pool().typeBoolean(), Op.A_STACK);
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
    protected boolean isRValue(Expression exprChild)
        {
        return m_fSuppressLValue || exprChild != lvalue;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        AstNode    nodeLeft     = lvalue;
        Expression exprLeftCopy = null;
        switch (getCategory())
            {
            case CondLeft:
            case InPlace:
                // we'll create a fake R-value expression later to handle all of the duties of
                // checking definite assignment, types, etc., so we need a pristine copy of the
                // L-value expression to use for that purpose
                exprLeftCopy = (Expression) nodeLeft.getLValueExpression().clone();
            }

        // a LValue represented by a statement indicates one or more variable declarations
        if (nodeLeft instanceof Statement)
            {
            assert nodeLeft instanceof VariableDeclarationStatement ||
                   nodeLeft instanceof MultipleLValueStatement;
            Statement lvalueOld = (Statement) nodeLeft;
            Statement lvalueNew = lvalueOld.validate(ctx, errs);
            if (lvalueNew != lvalueOld)
                {
                fValid &= lvalueNew != null;
                if (lvalueNew != null)
                    {
                    lvalue = nodeLeft = lvalueNew;
                    }
                }
            }

        // regardless of whether the LValue is a statement or expression, all L-Values must be able
        // to provide an expression as a representative form
        Expression exprLeft = nodeLeft.getLValueExpression();
        if (!exprLeft.isValidated())
            {
            // the type of l-value may have been narrowed in the current context, so let's try to
            // extract it and test the r-value with it; to prevent the lvalue from dropping that
            // narrowed information, we temporarily need to forget it's an lvalue
            TypeConstant[] atypeLeft = null;
            m_fSuppressLValue = true;
            try
                {
                atypeLeft = exprLeft.getImplicitTypes(ctx);
                }
            finally
                {
                m_fSuppressLValue = false;
                }

            if (atypeLeft != TypeConstant.NO_TYPES)
                {
                TypeFit fit = TypeFit.NoFit;
                switch (getCategory())
                    {
                    case Assign:
                    case CondLeft:
                    case InPlace:
                        // allow the r-value to resolve names based on the l-value type's
                        // contributions
                        ctx = ctx.enterInferring(atypeLeft[0]);
                        fit = rvalue.testFitMulti(ctx, atypeLeft);
                        ctx = ctx.exit();
                        break;

                    case CondExpr:      // e.g. "if (a : b) {...}"
                    case CondRight:     // e.g. "a := b;"
                        {
                        int            cLeft     = atypeLeft.length;
                        TypeConstant[] atypeTest = new TypeConstant[cLeft + 1];
                        atypeTest[0] = pool().typeBoolean();
                        System.arraycopy(atypeLeft, 0, atypeTest, 1, cLeft);

                        fit = rvalue.testFitMulti(ctx, atypeTest);
                        break;
                        }
                    }

                if (!fit.isFit())
                    {
                    // that didn't work; use the regular path
                    atypeLeft = TypeConstant.NO_TYPES;
                    }
                }

            Expression exprNew = exprLeft.validateMulti(ctx, atypeLeft, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                exprLeft = exprNew;
                }
            }
        lvalueExpr = exprLeft;
        exprLeft.requireAssignable(ctx, errs);

        Expression exprRight    = rvalue;
        Expression exprRightNew = null;
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
                        ctx = ctx.enterInferring(typeLeft);
                        }

                    exprRightNew = exprRight.validate(ctx, typeLeft, errs);

                    if (fInfer)
                        {
                        ctx = ctx.exit();
                        }
                    }
                else
                    {
                    // (LVal0, LVal1, ..., LValN) = RVal
                    exprRightNew = exprRight.validateMulti(ctx, exprLeft.getTypes(), errs);
                    }

                exprLeft.markAssignment(ctx, false, errs);
                break;

            case CondExpr:
            case CondRight:
                {
                // (LVal : RVal) or (LVal0, LVal1, ..., LValN : RVal)
                // TODO for ":=" the LValues must NOT be declarations!!! (they wouldn't be assigned)
                TypeConstant[] atypeLVals = exprLeft.getTypes();
                int            cLVals     = atypeLVals.length;
                int            cReq       = cLVals + 1;
                TypeConstant[] atypeReq   = new TypeConstant[cReq];
                atypeReq[0] = pool().typeBoolean();
                System.arraycopy(atypeLVals, 0, atypeReq, 1, cLVals);
                exprRightNew = exprRight.validateMulti(ctx, atypeReq, errs);
                exprLeft.markAssignment(ctx, true, errs);
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
                    ctx = ctx.enterInferring(typeLeft);
                    }

                BiExpression exprFakeRValue    = createBiExpression(exprLeftCopy, op, exprRight);
                Expression   exprFakeRValueNew = exprFakeRValue.validate(ctx, typeLeft, errs);
                if (exprFakeRValueNew instanceof BiExpression)
                    {
                    exprRightNew = ((BiExpression) exprFakeRValueNew).getExpression2();
                    }

                if (fInfer)
                    {
                    ctx = ctx.exit();
                    }

                break;
                }
            }

        if (exprRightNew != exprRight)
            {
            if (exprRightNew == null)
                {
                fValid = false;
                }
            else
                {
                rvalue = exprRightNew;
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        switch (getCategory())
            {
            case Assign:
                {
                // code gen optimization for the common case of a combined declaration & constant assignment
                // of a single value
                if (lvalueExpr.isSingle()
                        && rvalue.isConstant()
                        && lvalue instanceof VariableDeclarationStatement
                        && !((VariableDeclarationStatement) lvalue).hasRefAnnotations())
                    {
                    VariableDeclarationStatement lvalue = (VariableDeclarationStatement) this.lvalue;
                    StringConstant               idName = pool().ensureStringConstant(lvalue.getName());
                    code.add(new Var_IN(lvalue.getRegister(), idName, rvalue.toConstant()));
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

            case CondExpr:
                {
                Assignable[] LVals    = lvalueExpr.generateAssignables(ctx, code, errs);
                int          cLVals   = LVals.length;
                int          cAll     = cLVals + 1;
                Assignable[] LValsAll = new Assignable[cAll];
                LValsAll[0] = lvalueExpr.new Assignable(getConditionRegister());
                System.arraycopy(LVals, 0, LValsAll, 1, cLVals);
                if (fCompletes &= lvalueExpr.isCompletable())
                    {
                    rvalue.generateAssignments(ctx, code, LVals, errs);
                    fCompletes &= rvalue.isCompletable();
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

            case CondRight:
                {
                // "a := b" -> "if (a : b) {}" (without separate scope)
                Assignable[] LVals    = lvalueExpr.generateAssignables(ctx, code, errs);
                int          cLVals   = LVals.length;
                int          cAll     = cLVals + 1;
                Assignable[] LValsAll = new Assignable[cAll];
                Register     regCond  = new Register(pool().typeBoolean(), Op.A_STACK);
                LValsAll[0] = lvalueExpr.new Assignable(regCond);
                System.arraycopy(LVals, 0, LValsAll, 1, cLVals);
                if (fCompletes &= lvalueExpr.isCompletable())
                    {
                    rvalue.generateAssignments(ctx, code, LVals, errs);
                    fCompletes &= rvalue.isCompletable();
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
            default:
                throw new IllegalStateException("op=" + opIP.getId().TEXT);
            }

        Token opBi = new Token(opIP.getStartPosition(), opIP.getEndPosition(), idBi);
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
                return new RelOpExpression(exprLeft, opBi, exprRight);

            case COND_AND:
            case COND_OR:
                return new CondOpExpression(exprLeft, opBi, exprRight);

            case COND_ELSE:
                return new ElvisExpression(exprLeft, opBi, exprRight);

            default:
                throw new IllegalStateException("op=" + opBi.getId().TEXT);
            }
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

    public enum Category {Assign, CondExpr, CondLeft, CondRight, InPlace}

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
