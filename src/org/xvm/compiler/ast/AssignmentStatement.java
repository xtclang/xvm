package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Register;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Expression.Assignable;

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
            lvalue = new NameExpression(stmt.getNameToken(), stmt.getRegister());
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
                    LVals.set(i, new NameExpression(stmt.getNameToken(), stmt.getRegister()));
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
    public boolean isSimple()
        {
        return op.getId() == Id.ASN;
        }

    /**
     * @return true iff the assignment statement uses the ":" operator
     */
    public boolean isConditional()
        {
        return op.getId() == Id.COLON;
        }

    public Register ensureConditionRegister()
        {
        Register reg = m_regCond;
        if (reg == null)
            {
            if (!isConditional())
                {
                throw new IllegalStateException("op=\"" + op.getValueText() + '\"');
                }

            reg = new Register(pool().typeBoolean());
            }

        return reg;
        }

    public AstNode getLValue()
        {
        return lvalue;
        }

    public Token getOp()
        {
        return op;
        }

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
        return exprChild != lvalue;
        }

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // a LValue represented by a statement indicates one or more variable declarations
        AstNode nodeLeft = lvalue;
        if (nodeLeft instanceof Statement)
            {
            assert nodeLeft instanceof VariableDeclarationStatement || nodeLeft instanceof MultipleLValueStatement;
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
        Expression exprLeft = lvalue.getLValueExpression();
        if (!exprLeft.isValidated())
            {
            Expression exprNew = exprLeft.validate(ctx, null, errs);
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

        Expression rvalueOld = rvalue;
        Expression rvalueNew;
        if (isSimple())
            {
            if (exprLeft.isSingle())
                {
                // LVal = RVal (or some other assignment operator, not ':')
                TypeConstant typeLeft = exprLeft.getType();
                boolean      fInfer   = typeLeft != null;
                if (fInfer)
                    {
                    // allow the r-value to resolve names based on the l-value type's contributions
                    ctx = ctx.enterInferring(typeLeft);
                    }

                rvalueNew = rvalueOld.validate(ctx, typeLeft, errs);

                if (fInfer)
                    {
                    ctx = ctx.exitScope();
                    }
                }
            else
                {
                // (LVal0, LVal1, ..., LValN) = RVal
                rvalueNew = rvalueOld.validateMulti(ctx, exprLeft.getTypes(), errs);
                }
            }
        else if (isConditional())
            {
            // (LVal : RVal) or (LVal0, LVal1, ..., LValN : RVal)
            TypeConstant[] atypeLVals = exprLeft.getTypes();
            int            cLVals     = atypeLVals.length;
            int            cReq       = cLVals + 1;
            TypeConstant[] atypeReq   = new TypeConstant[cReq];
            atypeReq[0] = pool().typeBoolean();
            System.arraycopy(atypeLVals, 0, atypeReq, 1, cLVals);
            rvalueNew = rvalueOld.validateMulti(ctx, atypeReq, errs);
            }
        else
            {
            // TODO += *= etc.
            // TODO the LValues must NOT be declarations!!! (they wouldn't be assigned)
            throw notImplemented();
            }

        if (rvalueNew != rvalueOld)
            {
            fValid &= rvalueNew != null;
            if (rvalueNew != null)
                {
                rvalue = rvalueNew;
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        // code gen optimization for combined declaration & constant assignment of a single value
        if (isSimple() && lvalueExpr.isSingle() && rvalue.isConstant() && lvalue instanceof VariableDeclarationStatement)
            {
            // TODO see cut and paste in cp.txt
            }

        if (isSimple())
            {
            if (lvalue instanceof Statement)
                {
                fCompletes = ((Statement) lvalue).emit(ctx, fCompletes, code, errs);
                }

            Assignable[] LVals = lvalueExpr.generateAssignables(ctx, code, errs);
            if (fCompletes &= !lvalueExpr.isAborting())
                {
                rvalue.generateAssignments(ctx, code, LVals, errs);
                fCompletes &= !rvalue.isAborting();
                }
            }
        else if (isConditional())
            {
            Assignable[] LVals    = lvalueExpr.generateAssignables(ctx, code, errs);
            int          cLVals   = LVals.length;
            int          cAll     = cLVals + 1;
            Assignable[] LValsAll = new Assignable[cAll];
            LValsAll[0] = new Assignable(ensureConditionRegister());
            System.arraycopy(LVals, 0, LValsAll, 1, cLVals);
            if (fCompletes &= !lvalueExpr.isAborting())
                {
                rvalue.generateAssignments(ctx, code, LVals, errs);
                fCompletes &= !rvalue.isAborting();
                }
            }
        else
            {
            // TODO += *= etc.
            throw notImplemented();
            }

        return fCompletes;
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

    protected AstNode    lvalue;
    protected Expression lvalueExpr;
    protected Token      op;
    protected Expression rvalue;
    protected boolean    term;

    private transient VariableDeclarationStatement[] m_decls;
    private transient Register                       m_regCond;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssignmentStatement.class, "lvalue", "lvalueExpr", "rvalue");
    }
