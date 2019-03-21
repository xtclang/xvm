package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "if" statement.
 */
public class IfStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, AstNode cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, AstNode cond, StatementBlock stmtThen, Statement stmtElse)
        {
        this.keyword  = keyword;
        this.cond     = cond;
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return stmtElse == null ? stmtThen.getEndPosition() : stmtElse.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    /**
     * @return the label for the else
     */
    public Label getElseLabel()
        {
        Label label = m_labelElse;
        if (label == null)
            {
            m_labelElse = label = new Label("else_if_" + getLabelId());
            }
        return label;
        }

    private int getLabelId()
        {
        int n = m_nLabel;
        if (n == 0)
            {
            m_nLabel = n = ++s_nLabelCounter;
            }
        return n;
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        // only expression is the condition
        return true;
        }

    @Override
    protected Label getShortCircuitLabel(Context ctx, Expression exprChild)
        {
        // TODO snap-shot the assignment-info-delta
        return getElseLabel();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        ctx = ctx.enter();

        // the condition is either a boolean expression or an assignment statement whose R-value is
        // a multi-value with the first value being a boolean
        if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtOld = (AssignmentStatement) cond;
            AssignmentStatement stmtNew = (AssignmentStatement) stmtOld.validate(ctx, errs);
            if (stmtNew == null)
                {
                fValid = false;
                }
            else
                {
                if (stmtNew != stmtOld)
                    {
                    cond = stmtNew;
                    }
                }
            }
        else
            {
            Expression exprOld = (Expression) cond;
            Expression exprNew = exprOld.validate(ctx, pool().typeBoolean(), errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else  if (exprNew != exprOld)
                {
                cond = exprNew;
                }
            }

        ctx = ctx.enterFork(true);
        Statement stmtThenNew = stmtThen.validate(ctx, errs);
        ctx = ctx.exit();
        if (stmtThenNew == null)
            {
            fValid = false;
            }
        else
            {
            stmtThen = stmtThenNew;
            }

        if (stmtElse != null)
            {
            ctx = ctx.enterFork(false);
            Statement stmtElseNew = stmtElse.validate(ctx, errs);
            ctx = ctx.exit();
            if (stmtElseNew == null)
                {
                fValid = false;
                }
            else
                {
                stmtElse = stmtElseNew;
                }
            }

        ctx = ctx.exit();

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (cond instanceof Expression && ((Expression) cond).isConstant())
            {
            // "if (false) {stmtThen}" is optimized out altogether.
            // "if (false) {stmtThen} else {stmtElse}" is compiled as "{stmtElse}"
            // "if (true) {stmtThen}" is compiled as "{stmtThen}"
            // "if (true) {stmtThen} else {stmtElse}" is compiled as "{stmtThen}"
            if (((Expression) cond).isConstantTrue())
                {
                return stmtThen.completes(ctx, fReachable, code, errs);
                }
            else
                {
                assert ((Expression) cond).isConstantFalse();
                return stmtElse == null
                        ? fReachable
                        : stmtElse.completes(ctx, fReachable, code, errs);
                }
            }

        // "if (cond) {stmtThen}" is compiled as:
        //
        //   ENTER                  // iff cond specifies that it needs a scope
        //   [cond]
        //   JMP_FALSE cond Else    // this line or similar would be generated as part of [cond]
        //   [stmtThen]
        //   Else:
        //   Exit:
        //   EXIT                   // iff cond specifies that it needs a scope
        //
        // "if (cond) {stmtThen} else {stmtElse}" is compiled as:
        //
        //   ENTER                  // iff cond specifies that it needs a scope
        //   [cond]
        //   JMP_FALSE cond Else    // this line or similar would be generated as part of [cond]
        //   [stmtThen]
        //   JMP Exit
        //   Else:
        //   [stmtElse]
        //   Exit:
        //   EXIT                   // iff cond specifies that it needs a scope
        Label labelElse = getElseLabel();
        Label labelExit = new Label();

        boolean fScope = cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations();
        if (fScope)
            {
            code.add(new Enter());
            }

        boolean fCompletesCond;
        if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtCond = (AssignmentStatement) cond;
            fCompletesCond = stmtCond.completes(ctx, fReachable, code, errs);
            code.add(new JumpFalse(stmtCond.getConditionRegister(), labelElse));
            }
        else
            {
            Expression exprCond = (Expression) cond;
            fCompletesCond = exprCond.isCompletable();
            exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
            }

        boolean fCompletesThen = stmtThen.completes(ctx, fCompletesCond, code, errs);
        if (stmtElse != null)
            {
            code.add(new Jump(labelExit));
            }

        code.add(labelElse);
        boolean fCompletesElse = stmtElse == null
                ? fCompletesCond
                : stmtElse.completes(ctx, fCompletesCond, code, errs);

        code.add(labelExit);
        if (fScope)
            {
            code.add(new Exit());
            }

        return fCompletesThen | fCompletesElse;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("if (")
          .append(cond)
          .append(")\n")
          .append(indentLines(stmtThen.toString(), "    "));

        if (stmtElse != null)
            {
            if (stmtElse instanceof IfStatement)
                {
                sb.append("\nelse ")
                  .append(stmtElse);
                }
            else
                {
                sb.append("\nelse\n")
                  .append(indentLines(stmtElse.toString(), "    "));
                }
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     keyword;
    protected AstNode   cond;
    protected Statement stmtThen;
    protected Statement stmtElse;

    private static int s_nLabelCounter;
    private transient int   m_nLabel;
    private transient Label m_labelElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "cond", "stmtThen", "stmtElse");
    }
