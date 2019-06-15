package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Assignment;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.IP_Inc;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;
import org.xvm.asm.op.Var_IN;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 */
public class WhileStatement
        extends ConditionalStatement
        implements LabelAble
    {
    // ----- constructors --------------------------------------------------------------------------

    public WhileStatement(Token keyword, List<AstNode> conds, StatementBlock block)
        {
        this(keyword, conds, block, block.getEndPosition());
        }

    public WhileStatement(Token keyword, List<AstNode> conds, StatementBlock block, long lEndPos)
        {
        super(keyword, conds);
        this.block   = block;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this is a do-while loop, and not just a while loop
     */
    public boolean isDoWhile()
        {
        return keyword.getId() == Token.Id.DO;
        }

    @Override
    public boolean isNaturalGotoStatementTarget()
        {
        return true;
        }

    @Override
    public Label ensureContinueLabel(Context ctxOrigin)
        {
        Context ctxDest = getValidationContext();
        assert ctxDest != null;

        // generate a delta of assignment information for the jump
        Map<String, Assignment> mapAsn = ctxOrigin.prepareJump(ctxDest);

        // record the jump that landed on this statement by recording its assignment impact
        if (m_listContinues == null)
            {
            m_listContinues = new ArrayList<>();
            }
        m_listContinues.add(mapAsn);

        return getContinueLabel();
        }

    /**
     * @return the continue label for this statement
     */
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label("continue_while_" + getLabelId());
            }
        return label;
        }

    public Label getRepeatLabel()
        {
        Label label = m_labelRepeat;
        if (label == null)
            {
            m_labelRepeat = label = new Label("repeat_while_" + getLabelId());
            }
        return label;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- LabelAble methods ---------------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String sName)
        {
        return sName.equals("first") || sName.equals("count");
        }

    @Override
    public Register getLabelVar(String sName)
        {
        assert hasLabelVar(sName);

        boolean fFirst = sName.equals("first");

        Register reg = fFirst ? m_regFirst : m_regCount;
        if (reg == null)
            {
            // this occurs only during validate()
            assert m_ctxLabelVars != null;

            String sLabel = ((LabeledStatement) getParent()).getName();
            Token  tok    = new Token(keyword.getStartPosition(), keyword.getEndPosition(), Id.IDENTIFIER, sLabel + '.' + sName);

            reg = new Register(fFirst ? pool().typeBoolean() : pool().typeInt());
            m_ctxLabelVars.registerVar(tok, reg, m_errsLabelVars);

            if (fFirst)
                {
                m_regFirst = reg;
                }
            else
                {
                m_regCount = reg;
                }
            }

        return reg;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        ctx = ctx.enterLoop();

        // save off the current context and errors, in case we have to lazily create some loop vars
        m_ctxLabelVars  = ctx;
        m_errsLabelVars = errs;

        if (isDoWhile())
            {
            // block comes first for "do..while()"
            fValid &= validateBody(ctx, false, errs);
            }

        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            AstNode cond = getCondition(i);

            // the condition is either a boolean expression or an assignment statement whose R-value
            // is a multi-value with the first value being a boolean
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtOld = (AssignmentStatement) cond;
                AssignmentStatement stmtNew = (AssignmentStatement) stmtOld.validate(ctx, errs);
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

        if (!isDoWhile())
            {
            // block comes after for "while()"
            fValid &= validateBody(ctx, true, errs);
            }

        // if the condition itself required a scope, then complete that scope
        ctx = ctx.exit();

        // lazily created loop vars are only created inside the validation of this statement
        m_ctxLabelVars  = null;
        m_errsLabelVars = null;

        return fValid
                ? this
                : null;
        }

    /**
     * Validate the "body" portion of the while() or do..while() statement.
     *
     * @param ctx     the validation context
     * @param fScope  true if the body gets its own scope
     * @param errs    the error list to log any errors to
     *
     * @return true if the body validated without an error that should cause the validation to abort
     */
    protected boolean validateBody(Context ctx, boolean fScope, ErrorListener errs)
        {
        boolean fValid = true;
        if (fScope)
            {
            ctx = ctx.enterFork(true);
            }
        else
            {
            block.suppressScope();
            }

        Statement blockNew = block.validate(ctx, errs);
        if (blockNew == null)
            {
            fValid = false;
            }
        else
            {
            block = (StatementBlock) blockNew;
            }

        if (fScope)
            {
            ctx = ctx.exit();
            }

        List<Map<String, Assignment>> listContinues = m_listContinues;
        if (listContinues != null)
            {
            for (Map<String, Assignment> mapAsn : listContinues)
                {
                ctx.merge(mapAsn);
                }
            }

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean  fCompletes    = fReachable;
        boolean  fDoWhile      = isDoWhile();
        Register regFirst      = m_regFirst;
        Register regCount      = m_regCount;
        boolean  fHasLabelVars = regFirst != null || regCount != null;

        // any condition of false results in false (as long as all conditions up to that point are
        // constant); all condition of true results in true (as long as all conditions are constant)
        boolean fAlwaysTrue  = true;
        boolean fAlwaysFalse = true;
        for (AstNode cond : conds)
            {
            if (cond instanceof Expression && ((Expression) cond).isConstant())
                {
                if (((Expression) cond).isConstantFalse())
                    {
                    fAlwaysTrue = false;
                    break;
                    }
                else
                    {
                    assert ((Expression) cond).isConstantTrue();
                    fAlwaysFalse = false;
                    }
                }
            else
                {
                fAlwaysTrue  = false;
                fAlwaysFalse = false;
                break;
                }
            }

        if (fAlwaysFalse)
            {
            if (!fDoWhile)
                {
                // while(false) {body} - optimized out completely (unreachable)
                block.completes(ctx, false, code, errs);
                return fCompletes;
                }

            // do {body} while(false)
            //
            //   ENTER
            //   VAR_IN first true      ; optional
            //   VAR_IN count 0         ; optional
            //   [body]
            //   EXIT
            //   Continue:
            //   Break:
            code.add(new Enter());
            emitLabelVarCreation(code, regFirst, regCount);
            fCompletes = block.completes(ctx, fCompletes, code, errs);
            code.add(new Exit());
            code.add(getContinueLabel());
            return fCompletes;
            }

        if (fAlwaysTrue)
            {
            // while(true) {body}
            // do {body} while(true)
            //
            //   ENTER
            //   VAR_IN first true      ; optional
            //   VAR_IN count 0         ; optional
            //   JMP First              ; optional
            //   Repeat:
            //   Continue:
            //   MOV false first        ; optional
            //   IP_INC count           ; optional
            //   First:                 ; optional
            //   [body]
            //   JMP Repeat
            //   EXIT
            //   Break:
            code.add(new Enter());
            Label labelInit = emitLabelVarCreation(code, regFirst, regCount);
            if (labelInit != null)
                {
                code.add(new Jump(labelInit));
                }
            code.add(getRepeatLabel());
            code.add(getContinueLabel());
            emitLabelVarUpdate(code, regFirst, regCount, labelInit);
            block.suppressScope();
            block.completes(ctx, fCompletes, code, errs);
            code.add(new Jump(getRepeatLabel()));
            code.add(new Exit());
            return false;     // while(true) never completes naturally
            }

        if (fDoWhile)
            {
            // do {body} while(cond);
            //
            //   ENTER
            //   VAR_IN first true      ; optional
            //   VAR_IN count 0         ; optional
            //   JMP First              ; optional
            //   Repeat:
            //   MOV false first        ; optional
            //   IP_INC count           ; optional
            //   First:                 ; optional
            //   [body]                 ; body's scope is explicitly suppressed
            //   Continue:
            //   [cond]
            //   JMP_TRUE cond Repeat
            //   EXIT
            //   Break:
            code.add(new Enter());
            Label labelInit = emitLabelVarCreation(code, regFirst, regCount);
            if (labelInit != null)
                {
                code.add(new Jump(labelInit));
                }
            code.add(getRepeatLabel());
            emitLabelVarUpdate(code, regFirst, regCount, labelInit);

            // we explicitly do NOT check the block completion, since our completion is not dependent on
            // the block's ability to complete (since the loop may execute zero times)
            block.completes(ctx, fCompletes, code, errs);

            code.add(getContinueLabel());
            fCompletes = emitConditionTest(ctx, fCompletes, code, errs);
            code.add(new Exit());
            return fCompletes;
            }

        // while(cond) {body}
        //
        //   ENTER                  ; omitted if no declarations
        //   VAR_IN first true      ; optional
        //   VAR_IN count 0         ; optional
        //   [cond:decl]            ; omitted if no declarations
        //   JMP First (or Continue, if there is no First)
        //   Repeat:
        //   [body]
        //   Continue:
        //   MOV false first        ; optional
        //   IP_INC count           ; optional
        //   First:                 ; optional
        //   [cond]
        //   JMP_TRUE cond Repeat
        //   EXIT                   ; omitted if no declarations
        //   Break:
        boolean fHasDecls = conds.stream().anyMatch(cond ->
                cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations());
        boolean fOwnScope = fHasDecls || fHasLabelVars;
        if (fOwnScope)
            {
            code.add(new Enter());
            }
        Label labelInit = emitLabelVarCreation(code, regFirst, regCount);
        if (fHasDecls)
            {
            for (AstNode cond : conds)
                {
                if (cond instanceof AssignmentStatement)
                    {
                    for (VariableDeclarationStatement stmtDecl : ((AssignmentStatement) cond).takeDeclarations())
                        {
                        fCompletes &= stmtDecl.completes(ctx, fCompletes, code, errs);
                        }
                    }
                }
            }
        code.add(new Jump(labelInit == null ? getContinueLabel() : labelInit));
        code.add(getRepeatLabel());

        // we explicitly do NOT check the block completion, since our completion is not dependent on
        // the block's ability to complete (since the loop may execute zero times)
        block.completes(ctx, fCompletes, code, errs);

        code.add(getContinueLabel());
        emitLabelVarUpdate(code, regFirst, regCount, labelInit);
        fCompletes = emitConditionTest(ctx, fCompletes, code, errs);
        if (fOwnScope)
            {
            code.add(new Exit());
            }
        return fCompletes;
        }

    /**
     * Internal method: create the variables for the "first" and "count" label variables, but only
     * if necessary.
     *
     * @param code      the code to emit
     * @param regFirst  the (optional) register for the "first" variable
     * @param regCount  the (optional) register for the "count" variable
     *
     * @return a label that skips the variable update for the first iteration iff either "first" or
     *         "count" exists, otherwise null
     */
    private Label emitLabelVarCreation(Code code, Register regFirst, Register regCount)
        {
        ConstantPool pool = pool();

        if (regFirst != null)
            {
            StringConstant name = pool.ensureStringConstant(
                    ((LabeledStatement) getParent()).getName() + ".first");
            code.add(new Var_IN(m_regFirst, name, pool.valTrue()));
            }

        if (regCount != null)
            {
            StringConstant name = pool.ensureStringConstant(
                    ((LabeledStatement) getParent()).getName() + ".count");
            code.add(new Var_IN(m_regCount, name, pool.val0()));
            }

        return regFirst == null && regCount == null ? null : new Label("first_while_" + getLabelId());
        }

    /**
     * Internal method: update the variables for the "first" and "count" label variables, but only
     * if necessary.
     *
     * @param code      the code to emit
     * @param regFirst  the (optional) register for the "first" variable
     * @param regCount  the (optional) register for the "count" variable
     *
     * @param labelInit  the label previously returned from {@link #emitLabelVarCreation}
     */
    private void emitLabelVarUpdate(Code code, Register regFirst, Register regCount, Label labelInit)
        {
        if (labelInit != null)
            {
            if (regFirst != null)
                {
                code.add(new Move(pool().valFalse(), regFirst));
                }
            if (regCount != null)
                {
                code.add(new IP_Inc(regCount));
                }
            code.add(labelInit);
            }
        }

    private boolean emitConditionTest(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;
        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            AstNode cond  = getCondition(i);
            boolean fLast = i == c-1;
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtCond = (AssignmentStatement) cond;
                fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);
                if (fLast)
                    {
                    code.add(new JumpTrue(stmtCond.getConditionRegister(), getRepeatLabel()));
                    }
                else
                    {
                    code.add(new JumpFalse(stmtCond.getConditionRegister(), getEndLabel()));
                    }
                }
            else
                {
                Expression exprCond = (Expression) cond;
                if (fLast)
                    {
                    exprCond.generateConditionalJump(ctx, code, getRepeatLabel(), true, errs);
                    }
                else
                    {
                    exprCond.generateConditionalJump(ctx, code, getEndLabel(), false, errs);
                    }
                fCompletes &= exprCond.isCompletable();
                }
            }

        return fCompletes;
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (keyword.getId() == Token.Id.WHILE || keyword.getId() == Token.Id.FOR)
            {
            sb.append(keyword.getId().TEXT)
              .append(" (")
              .append(conds.get(0));

            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
                }

            sb.append(")\n")
              .append(indentLines(block.toString(), "    "));
            }
        else
            {
            assert keyword.getId() == Token.Id.DO;

            sb.append("do")
              .append('\n')
              .append(indentLines(block.toString(), "    "))
              .append("\nwhile (");

            sb.append(conds.get(0));
            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
                }
            sb.append(");");
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return keyword.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected StatementBlock block;
    protected long           lEndPos;

    private transient Label m_labelContinue;
    private transient Label m_labelRepeat;

    private transient Context       m_ctxLabelVars;
    private transient ErrorListener m_errsLabelVars;
    private transient Register      m_regFirst;
    private transient Register      m_regCount;

    /**
     * Generally null, unless there is a "continue" that jumps to this statement.
     */
    private transient List<Map<String, Assignment>> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "conds", "block");
    }
