package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
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

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;

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
    public Label ensureContinueLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        Context ctxDest = ensureValidationContext();

        if (ctxOrigin.isReachable())
            {
            // generate a delta of assignment information for the jump
            Map<String, Assignment> mapAsn = ctxOrigin.prepareJump(ctxDest);

            // record the jump that landed on this statement by recording its assignment impact
            if (m_listContinues == null)
                {
                m_listContinues = new ArrayList<>();
                }
            m_listContinues.add(new SimpleEntry<>(nodeOrigin, mapAsn));
            }

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

            reg = new Register(fFirst ? pool().typeBoolean() : pool().typeCInt64());
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
        // there are a set of assumptions coming in:
        // - the loop is actually going to loop, as in "it is able to execute more than once"
        //   -> while(False) obviously does not result in any execution of the body whatsoever (the
        //      value False, or any other expression that validates to compile time False)
        //   -> do..while(False) will execute once, but won't loop (the value False, or any other
        //      expression that validates to compile time False)
        //   -> a loop without a reachable "continue" and has an unavoidable "break" / "return" /
        //      "throw" / etc. will not loop
        //   -> variable assignment with respect to "assigned once" inside a loop that doesn't
        //      actually loop is different than variable assignment in a loop that does loop
        // - the loop condition is a non-constant that can evaluate to either True or False
        //   -> if we prove that the condition is a constant, we can make a different set of
        //      assumptions
        //   -> in the case of do..while(cond), the "cond" can't be evaluated until after the body,
        //      so the assumptions used to evaluate the body may have all been wrong
        // - there may be type assumptions coming in that are affected by the execution of the loop
        //   body
        //    | Object o = foo();
        //    | if (o.is(String))
        //    |     {
        //    |     while (True)
        //    |         {
        //    |         Int i = o.size; // sure, we still assume that o is a String
        //    |         o = bar();      // this means that we can't assume that o is still a String
        //    |         }
        //    |     }
        //   -> any assumption that we rely on in the loop that changes by the point that the loop
        //      reaches the point that the loop will begin again needs to invalidate the previous
        //      assumption, and the entire loop needs to then be re-evaluated (so a clone of the
        //      AST is required)
        //   -> obviously, this is not a concern if the loop does not actually end up looping (in
        //      the various ways described above)

        boolean fDoWhile = isDoWhile();

        // each attempt to validate the loop will log errors into a temporary error list; whichever
        // run is the "keeper" will have its temporary errors moved over (relogged) into the
        // original error listener
        ErrorListener  errsOrig = errs;

        // by holding on to the original context, we can determine the impact on the incoming
        // context that would occur by the beginning of the second iteration of the loop, and
        // use that information to correctly provide forward-looking assignment information to
        // AST nodes nested further down the tree, including e.g. lambdas that may make different
        // capture decisions based on that data
        Context                 ctxOrig    = ctx;
        Map<String, Assignment> mapLoopAsn = new HashMap<>();
        Map<String, Argument>   mapLoopArg = new HashMap<>();

        ctxOrig.prepareJump(ctxOrig, mapLoopAsn, mapLoopArg);

        // the validated conditions will end up in this temporary array; each will be a clone of the
        // original in "conds"
        int           cConds    = getConditionCount();
        List<AstNode> condsOrig = conds;

        // the body of the loop will not have its own scope; scope is provided by the loop; this
        // allows the variables in the body to be utilized in the conditions of a do..while() loop
        block.suppressScope();
        StatementBlock blockOrig = block;

        // assume that the while or do..while statement actually loops (we may find out otherwise)
        boolean fLoops = true;

        // don't let this repeat ad nauseam
        int cTries = 0;

        while (true)
            {
            boolean fValid  = true;
            boolean fRepeat = false;

            // clone the condition(s) and the body
            conds = new ArrayList<>(cConds);
            for (AstNode cond : condsOrig)
                {
                conds.add(cond.clone());
                }
            block = (StatementBlock) blockOrig.clone();

            // create a temporary error list
            errs = errsOrig.branch(this);

            // we use a potentially unnecessary context here as a place to jam in any assumptions
            // that we learned on a previous trial run through the loop
            ctx = ctxOrig.enter();
            ctx.merge(mapLoopAsn, mapLoopArg);
            int cExits  = 1;

            // the current context and error list are required by getLabelVar() if, in the process
            // of validation, one of the nested AST nodes requires a loop variable
            m_ctxLabelVars  = ctx;
            m_errsLabelVars = errs;
            m_listContinues = null;

            // either enter normal or loop, depending on the assumption
            if (fLoops)
                {
                ctx = ctx.enterLoop();
                ++cExits;
                }

            // we have two parts to validate, the conditions and the block. unfortunately, these
            // come in two different orders, either the conditions first (for a while loop) followed
            // by the block, or the block first (for a do..while) followed by the conditions.
            boolean fAlwaysTrue = false;
            for (int iPart = 1; iPart <= 2; ++iPart)
                {
                if ((iPart == 2) == fDoWhile)
                    {
                    // validate the condition(s); note that in the case of "while-do", the test
                    // expression plays a role of an "if", since the block cannot be entered
                    // if this expression evaluates to "false"; in the case of "do-while", it
                    // is *only* used to calculate the impact of the "while"
                    ctx = ctx.enterIf();
                    ++cExits;

                    for (int i = 0; i < cConds; ++i)
                        {
                        AstNode condOld = conds.get(i);
                        AstNode condNew;

                        // the condition is either a boolean expression or an assignment
                        // statement whose R-value is a multi-value with the first value
                        // being a boolean
                        if (condOld instanceof AssignmentStatement stmtCond)
                            {
                            if (stmtCond.isNegated())
                                {
                                ctx = ctx.enterNot();
                                }

                            condNew = stmtCond.validate(ctx, errs);

                            if (stmtCond.isNegated())
                                {
                                ctx = ctx.exit();
                                }
                            }
                        else
                            {
                            // the node must be cloned, because we may end up repeating this
                            // validation process with a different set of assumptions
                            condNew = ((Expression) condOld).validate(ctx, pool().typeBoolean(), errs);
                            }

                        if (condNew == null)
                            {
                            fValid = false;
                            }
                        else
                            {
                            if (condNew != condOld)
                                {
                                conds.set(i, condNew);
                                }

                            if (condNew instanceof Expression exprCond && exprCond.isConstant())
                                {
                                if (exprCond.isConstantFalse())
                                    {
                                    if (fDoWhile)
                                        {
                                        // do..while(False) does not loop
                                        if (fLoops)
                                            {
                                            // need to repeat the entire validation, now that we
                                            // know that the loop doesn't loop
                                            fLoops  = false;
                                            fRepeat = true;
                                            }
                                        }
                                    else
                                        {
                                        // while(False) is illegal because it cannot ever execute
                                        condNew.log(errs, Severity.ERROR, Compiler.ILLEGAL_WHILE_CONDITION);
                                        fValid = false;
                                        }
                                    }
                                else
                                    {
                                    assert ((Expression) condNew).isConstantTrue();
                                    fAlwaysTrue = true;
                                    }
                                }
                            }
                        }

                    // a "while(cond) {...}" loop only transfers the "when true" branch of
                    // assignment from the condition to the body of the loop
                    if (fAlwaysTrue)
                        {
                        if (block.getStatements().isEmpty())
                            {
                            log(errs, Severity.ERROR, Compiler.INFINITE_LOOP);
                            errs.merge();
                            return null;
                            }
                        ctx = ctx.enterInfiniteLoop();
                        }
                    else
                        {
                        ctx = ctx.enterFork(true);
                        }
                    ++cExits;
                    }
                else // validate the block
                    {
                    // remember whether the block was even reachable
                    boolean fReachable = ctx.isReachable();

                    // validate the block
                    StatementBlock blockNew = (StatementBlock) block.validate(ctx, errs);
                    if (blockNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        block = blockNew;
                        }

                    // apply the assignment contributions from the various continue statements, if any
                    boolean fContinues = false;
                    if (m_listContinues != null)
                        {
                        for (Iterator<Entry<AstNode, Map<String, Assignment>>> iter = m_listContinues.iterator();
                                iter.hasNext(); )
                            {
                            Entry<AstNode, Map<String, Assignment>> entry = iter.next();
                            if (entry.getKey().isDiscarded())
                                {
                                iter.remove();
                                }
                            else
                                {
                                fContinues = true;
                                ctx.merge(entry.getValue());
                                }
                            }
                        }

                    // if the block was reachable, but it didn't complete, and there are no
                    // "continue" statements, then this loop doesn't actually loop
                    if (fLoops && fReachable && !ctx.isReachable() && !fContinues)
                        {
                        // need to re-validate the whole thing, with the knowledge that it does
                        // not loop
                        fLoops  = false;
                        fRepeat = true;
                        }
                    }
                }

            // see if there are any assignments that would change our starting assumptions
            Map<String, Assignment> mapAsnAfter = new HashMap<>();
            Map<String, Argument>   mapArgAfter = new HashMap<>();
            ctx.prepareJump(ctxOrig, mapAsnAfter, mapArgAfter);

            if (!mapAsnAfter.equals(mapLoopAsn))
                {
                mapLoopAsn = mapAsnAfter;
                mapLoopArg = mapArgAfter;
                fRepeat    = true;
                }

            // don't let this repeat forever
            if (++cTries > 10 && fRepeat)
                {
                log(errs, Severity.ERROR, Compiler.FATAL_ERROR);
                fValid  = false;
                fRepeat = false;
                }

            if (fRepeat)
                {
                // discard the clones created in this pass
                for (AstNode cond : conds)
                    {
                    cond.discard(true);
                    }
                block.discard(true);
                }
            else
                {
                // discard the original nodes (we cloned them already)
                for (AstNode cond : condsOrig)
                    {
                    cond.discard(true);
                    }
                blockOrig.discard(true);

                // lazily created loop vars are only created inside the validation of this statement
                m_ctxLabelVars  = null;
                m_errsLabelVars = null;

                // unwind local contexts
                while (cExits-- > 0)
                    {
                    ctx = ctx.exit();
                    }
                assert ctx == ctxOrig;

                if (fAlwaysTrue)
                    {
                    // doesn't complete normally; the breaks will be processed by
                    // Statement.validate()
                    ctx.setReachable(false);
                    }
                errs.merge();
                return fValid ? this : null;
                }
            }
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
            if (cond instanceof Expression exprCond && exprCond.isConstant())
                {
                if (exprCond.isConstantFalse())
                    {
                    fAlwaysTrue = false;
                    break;
                    }
                else
                    {
                    assert exprCond.isConstantTrue();
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

            Op opLast = code.getLastOp();

            block.suppressScope();
            block.completes(ctx, fCompletes, code, errs);

            if (code.getLastOp() == opLast)
                {
                // the block didn't add any ops; this is just an infinite loop
                log(errs, Severity.ERROR, Compiler.INFINITE_LOOP);
                }
            else
                {
                code.add(new Jump(getRepeatLabel()));
                code.add(new Exit());
                }
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
                cond instanceof AssignmentStatement stmtAsn && stmtAsn.hasDeclarations());
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
                if (cond instanceof AssignmentStatement stmtCond)
                    {
                    for (VariableDeclarationStatement stmtDecl : stmtCond.takeDeclarations())
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
            if (cond instanceof AssignmentStatement stmtCond)
                {
                fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);

                if (fLast)
                    {
                    code.add(stmtCond.isNegated()
                        ? new JumpFalse(stmtCond.getConditionRegister(), getRepeatLabel())
                        : new JumpTrue (stmtCond.getConditionRegister(), getRepeatLabel()));
                    }
                else
                    {
                    code.add(stmtCond.isNegated()
                        ? new JumpTrue (stmtCond.getConditionRegister(), getEndLabel())
                        : new JumpFalse(stmtCond.getConditionRegister(), getEndLabel()));
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
    private transient List<Entry<AstNode, Map<String, Assignment>>> m_listContinues;

    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "conds", "block");
    }
