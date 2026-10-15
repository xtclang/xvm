package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;

/**
 * An "if" statement.
 */
public class IfStatement
        extends ConditionalStatement {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, List<AstNode> conds, StatementBlock block) {
        this(keyword, conds, block, null);
    }

    public IfStatement(Token keyword, List<AstNode> conds, StatementBlock stmtThen, Statement stmtElse) {
        super(keyword, conds);
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
    }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getEndPosition() {
        return stmtElse == null ? stmtThen.getEndPosition() : stmtElse.getEndPosition();
    }

    /**
     * @return the label for the else
     */
    public Label getElseLabel() {
        Label label = m_labelElse;
        if (label == null) {
            m_labelElse = label = new Label("else_if_" + getLabelId());
        }
        return label;
    }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin) {
        if (stmtElse == null) {
            return ensureBreakLabel(nodeOrigin, ctxOrigin);
        }

        AstNode nodeChild = findChild(nodeOrigin);
        assert nodeChild != null;
        assert allowsShortCircuit(nodeChild);

        Context ctxDest = ensureValidationContext();
        Label   label   = getElseLabel();
        if (ctxOrigin.isReachable()) {
            // generate a delta of assignment information for the jump
            if (m_listShorts == null) {
                m_listShorts = new ArrayList<>();
            }
            m_listShorts.add(prepareJump(nodeOrigin, ctxOrigin, ctxDest, label));
        }

        return label;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        boolean fValid = true;

        ctx = ctx.enterIf();

        int cConditions = getConditionCount();
        for (int i = 0; i < cConditions; ++i) {
            AstNode cond = getCondition(i);

            if (i > 0) {
                ctx = ctx.enterAndIf();
            }

            AstNode validated = validateCondition(ctx, cond, errs);
            if (validated == null) {
                fValid = false;
            } else if (validated != cond) {
                conds.set(i, validated);
            }
        }

        ctx = ctx.enterFork(true);
        Statement stmtThenNew = stmtThen.validate(ctx, errs);
        ctx = ctx.exit();
        if (stmtThenNew == null) {
            fValid = false;
        } else {
            stmtThen = stmtThenNew;
        }

        while (--cConditions > 0) {
            ctx = ctx.exit(); // "and-if"s
        }

        // create a context for "else" even if there is no statement, thus facilitating a merge;
        // for example (see Array.x):
        //
        //   if (interval == Null)
        //     {
        //     interval = 0..size-1;
        // }
        //
        // at this point "interval" should be known to be not Null

        ctx = ctx.enterFork(false);
        if (stmtElse != null) {
            // short circuits land here
            // REVIEW CP if something was unassigned in the validation context, still unassigned
            //           at the point of the short, and assigned by the point that the condition
            //           was fully evaluated, it should be unassigned here, right?
            if (m_listShorts != null) {
                mergeJumps(ctx, m_listShorts);
            }

            Statement stmtElseNew = stmtElse.validate(ctx, errs);
            if (stmtElseNew == null) {
                fValid = false;
            } else {
                stmtElse = stmtElseNew;
            }
        }
        ctx = ctx.exit(); // "else"
        ctx.exit(); // "if"

        return fValid ? this : null;
    }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        // any condition of false results in false (as long as all conditions up to that point are
        // constant); all condition of true results in true (as long as all conditions are constant)
        boolean fAlwaysTrue = true;
        for (AstNode cond : conds) {
            if (cond instanceof Expression && ((Expression) cond).isConstant()) {
                if (((Expression) cond).isConstantFalse()) {
                    // "if (false) {stmtThen}" is optimized out altogether.
                    if (stmtElse == null) {
                        return fReachable;
                    }

                    // "if (false) {stmtThen} else {stmtElse}" is compiled as "{stmtElse}"
                    return stmtElse.completes(ctx, fReachable, code, errs);
                }

                assert ((Expression) cond).isConstantTrue();
            } else {
                fAlwaysTrue = false;
                break;
            }
        }

        // "if (true) {stmtThen}" is compiled as "{stmtThen}"
        // "if (true) {stmtThen} else {stmtElse}" is compiled as "{stmtThen}"
        if (fAlwaysTrue) {
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
        Label labelExit = new Label(getCodeContainerCounter());

        boolean fScope = false;
        for (AstNode cond : conds) {
            if (cond.isScopeRequired()) {
                code.add(new Enter());
                fScope = true;
                break;
            }
        }

        boolean fFirst       = true;
        boolean fReachesThen = fReachable;
        boolean fReachesElse = fReachable;
        for (AstNode cond : conds) {
            boolean fCompletes;
            if (cond instanceof AssignmentStatement stmtCond) {
                boolean fNegated = stmtCond.isNegated();
                Label   labelNeg = fNegated ? new Label("negated") : null;

                stmtCond.setConditionFalseLabel(fNegated ? labelNeg : labelElse);

                fCompletes = stmtCond.completes(ctx, fReachable, code, errs);

                assert stmtCond.isConditionFalseLabelTaken();

                if (fNegated) {
                    code.add(new Jump(labelElse));
                    code.add(labelNeg);
                }
            } else {
                Expression exprCond = (Expression) cond;
                if (exprCond.isConstantTrue()) {
                    // this condition is a no-op (go to the next condition, and if this was the
                    // first condition, then treat the next condition as the first condition)
                    continue;
                } else if (exprCond.isConstantFalse()) {
                    // this condition is the last condition, because "false" caps the list of
                    // conditions, making the rest unreachable
                    fReachesThen = false;
                    code.add(new Jump(labelElse));
                    break;
                } else {
                    fCompletes = exprCond.isCompletable();
                    exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
                }
            }

            fReachesThen &= fCompletes;
            if (fFirst) {
                fReachesElse &= fCompletes;
            }

            fFirst = false;
        }

        boolean fCompletesThen = fReachesThen;
        if (fReachesThen) {
            fCompletesThen = stmtThen.completes(ctx, true, code, errs);
            if (stmtElse != null) {
                code.add(new Jump(labelExit));
            }
        }

        boolean fCompletesElse = fReachesElse;
        code.add(labelElse);
        if (fReachesElse && stmtElse != null) {
            fCompletesElse = stmtElse.completes(ctx, true, code, errs);
        }

        code.add(labelExit);
        if (fScope) {
            code.add(new Exit());
        }

        return fCompletesThen | fCompletesElse;
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("if (")
          .append(conds.stream().map(Object::toString).collect(Collectors.joining(", ")))
          .append(")\n")
          .append(indentLines(stmtThen.toString(), "    "));

        if (stmtElse != null) {
            if (stmtElse instanceof IfStatement) {
                sb.append("\nelse ")
                  .append(stmtElse);
            } else {
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
     * Generally null, unless there is a short-circuit that jumps to this statement's else label.
     */
    private transient List<Break> m_listShorts;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "conds", "stmtThen", "stmtElse");
}
