package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "if" statement.
 */
public class IfStatement
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, List<AstNode> conds, StatementBlock block)
        {
        this(keyword, conds, block, null);
        }

    public IfStatement(Token keyword, List<AstNode> conds, StatementBlock stmtThen, Statement stmtElse)
        {
        super(keyword, conds);
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getEndPosition()
        {
        return stmtElse == null ? stmtThen.getEndPosition() : stmtElse.getEndPosition();
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

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        if (stmtElse == null)
            {
            return ensureBreakLabel(nodeOrigin, ctxOrigin);
            }

        AstNode nodeChild = findChild(nodeOrigin);
        assert nodeChild != null;
        assert allowsShortCircuit(nodeChild);

        Context ctxDest = ensureValidationContext();
        if (ctxOrigin.isReachable())
            {
            // generate a delta of assignment information for the jump
            if (m_listShorts == null)
                {
                m_listShorts = new ArrayList<>();
                }
            m_listShorts.add(new SimpleEntry<>(nodeOrigin, ctxOrigin.prepareJump(ctxDest)));
            }

        return getElseLabel();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        ctx = ctx.enterIf();

        int cConditions = getConditionCount();
        for (int i = 0; i < cConditions; ++i)
            {
            AstNode cond = getCondition(i);

            if (i > 0)
                {
                ctx = ctx.enterAnd().enterMultiConditionIf();
                }

            // the condition is either a boolean expression or an assignment statement whose R-value
            // is a multi-value with the first value being a boolean
            if (cond instanceof AssignmentStatement stmtOld)
                {
                if (stmtOld.isNegated())
                    {
                    ctx = ctx.enterNot();
                    }

                AssignmentStatement stmtNew = (AssignmentStatement) stmtOld.validate(ctx, errs);

                if (stmtOld.isNegated())
                    {
                    ctx = ctx.exit();
                    }
                if (stmtNew == null)
                    {
                    fValid = false;
                    }
                else if (stmtNew != stmtOld)
                    {
                    cond = stmtNew;
                    conds.set(i, cond);
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
                    conds.set(i, cond);
                    }
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

        while (--cConditions > 0)
            {
            ctx = ctx.exit().exit(); // "and-if"s
            }

        // create a context for "else" even if there is no statement, thus facilitating a merge;
        // for example (see Array.x):
        //
        //   if (interval == Null)
        //     {
        //     interval = 0..size-1;
        //     }
        //
        // at this point "interval" should be known to be not Null

        ctx = ctx.enterFork(false);
        if (stmtElse != null)
            {
            // short circuits land here
            // REVIEW CP if something was unassigned in the validation context, still unassigned
            //           at the point of the short, and assigned by the point that the condition
            //           was fully evaluated, it should be unassigned here, right?
            if (m_listShorts != null)
                {
                for (Entry<AstNode, Map<String, Assignment>> entry : m_listShorts)
                    {
                    if (!entry.getKey().isDiscarded())
                        {
                        ctx.merge(entry.getValue());
                        }
                    }
                }

            Statement stmtElseNew = stmtElse.validate(ctx, errs);
            if (stmtElseNew == null)
                {
                fValid = false;
                }
            else
                {
                stmtElse = stmtElseNew;
                }
            }
        ctx = ctx.exit(); // "else"
        ctx = ctx.exit(); // "if"

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // any condition of false results in false (as long as all conditions up to that point are
        // constant); all condition of true results in true (as long as all conditions are constant)
        boolean fAlwaysTrue = true;
        for (AstNode cond : conds)
            {
            if (cond instanceof Expression && ((Expression) cond).isConstant())
                {
                if (((Expression) cond).isConstantFalse())
                    {
                    // "if (false) {stmtThen}" is optimized out altogether.
                    // "if (false) {stmtThen} else {stmtElse}" is compiled as "{stmtElse}"
                    return stmtElse == null
                            ? fReachable
                            : stmtElse.completes(ctx, fReachable, code, errs);
                    }

                assert ((Expression) cond).isConstantTrue();
                }
            else
                {
                fAlwaysTrue = false;
                break;
                }
            }

        // "if (true) {stmtThen}" is compiled as "{stmtThen}"
        // "if (true) {stmtThen} else {stmtElse}" is compiled as "{stmtThen}"
        if (fAlwaysTrue)
            {
            return stmtThen.completes(ctx, fReachable, code, errs);
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

        boolean fScope         = false;
        boolean fCompletesThen = fReachable;
        boolean fCompletesElse = fReachable;
        boolean fFirst         = true;
        for (AstNode cond : conds)
            {
            if (cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations())
                {
                code.add(new Enter());
                fScope = true;
                break;
                }
            }

        for (AstNode cond : conds)
            {
            boolean fCompletes;
            if (cond instanceof AssignmentStatement stmtCond)
                {
                fCompletes = stmtCond.completes(ctx, fReachable, code, errs);
                if (stmtCond.isNegated())
                    {
                    code.add(new JumpTrue(stmtCond.getConditionRegister(), labelElse));
                    }
                else
                    {
                    code.add(new JumpFalse(stmtCond.getConditionRegister(), labelElse));
                    }
                }
            else
                {
                Expression exprCond = (Expression) cond;
                if (exprCond.isConstantTrue())
                    {
                    // this condition is a no-op (go to the next condition, and if this was the
                    // first condition, then treat the next condition as the first condition)
                    continue;
                    }
                else if (exprCond.isConstantFalse())
                    {
                    // this condition is the last condition, because "false" caps the list of
                    // conditions, making the rest unreachable
                    fCompletesThen = false;
                    code.add(new Jump(labelElse));
                    break;
                    }
                else
                    {
                    fCompletes = exprCond.isCompletable();
                    exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
                    }
                }

            fCompletesThen &= fCompletes;
            if (fFirst)
                {
                fCompletesElse &= fCompletes;
                }

            fFirst = false;
            }

        if (fCompletesThen)
            {
            fCompletesThen = stmtThen.completes(ctx, fCompletesThen, code, errs);
            if (stmtElse != null)
                {
                code.add(new Jump(labelExit));
                }
            }

        code.add(labelElse);
        if (fCompletesElse && stmtElse != null)
            {
            fCompletesElse = stmtElse.completes(ctx, fCompletesElse, code, errs);
            }

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
          .append(conds.get(0));
        for (int i = 1, c = conds.size(); i < c; ++i)
            {
            sb.append(", ")
              .append(conds.get(i));
            }
        sb.append(")\n")
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

    protected Statement stmtThen;
    protected Statement stmtElse;

    /**
     * The "else" label.
     */
    private transient Label m_labelElse;

    /**
     * Generally null, unless there is a short that jumps to this statement's else label.
     */
    private transient List<Map.Entry<AstNode, Map<String, Assignment>>> m_listShorts;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "conds", "stmtThen", "stmtElse");
    }
