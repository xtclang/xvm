package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

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
     * @return true iff the assignment statement uses the ":" operator
     */
    public boolean isConditional()
        {
        return op.getId() == Token.Id.COLON;
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

        // REVIEW does this have to support multiple assignment? (I think that it does...)

        Expression lvalueNew = lvalue.validate(ctx, null, errs);
        if (lvalueNew != lvalue)
            {
            fValid &= lvalueNew != null;
            if (lvalueNew != null)
                {
                lvalue = lvalueNew;
                }
            }

        // provide the l-value's type to the r-value so that it can "infer" its type as necessary,
        // and can validate that assignment can occur
        TypeConstant typeLeft = lvalue.getType();
        boolean      fInfer   = typeLeft != null;
        if (fInfer)
            {
            // allow the r-value to resolve names based on the l-value type's contributions
            ctx = ctx.enterInferring(typeLeft);
            }

        Expression rvalueNew = isConditional()
            ? rvalue.validateMulti(ctx, new TypeConstant[] {pool().typeBoolean(), typeLeft}, errs)
            : rvalue.validate(ctx, typeLeft, errs);
        if (rvalueNew != rvalue)
            {
            fValid &= rvalueNew != null;
            if (rvalueNew != null)
                {
                rvalue = rvalueNew;
                }
            }

        if (fInfer)
            {
            ctx = ctx.exitScope();
            }

        if (lvalue.isVoid())
            {
            lvalue.log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY,
                    Math.max(1, rvalue.getValueCount()), 0);
            }
        else
            {
            int cValues = lvalue.getValueCount();
            if (isConditional())
                {
                cValues++;
                }
            if (cValues == rvalue.getValueCount())
                {
                lvalue.requireAssignable(ctx, errs);
                }
            else
                {
                rvalue.log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY,
                    cValues, rvalue.getValueCount());
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        switch (getUsage())
            {
            case While:
            case If:
            case For:
            case Switch:
                // TODO
                throw notImplemented();

            case Standalone:
               break;
            }

        if (lvalue.isSingle() && op.getId() == Token.Id.ASN)
            {
            boolean    fCompletes = fReachable;
            Assignable asnL       = lvalue.generateAssignable(ctx, code, errs);
            if (fCompletes &= !lvalue.isAborting())
                {
                rvalue.generateAssignment(ctx, code, asnL, errs);
                fCompletes &= !rvalue.isAborting();
                }

            return fCompletes;
            }

        // REVIEW what is not implemented? multi-assignment?
        throw notImplemented();
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
    protected Token      op;
    protected Expression rvalue;
    protected boolean    term;

    private VariableDeclarationStatement[] m_decls;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssignmentStatement.class, "lvalue", "rvalue");
    }
