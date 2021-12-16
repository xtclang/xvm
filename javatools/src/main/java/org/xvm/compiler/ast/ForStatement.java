package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.xvm.asm.Assignment;
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
 * The traditional "for" statement.
 */
public class ForStatement
        extends ConditionalStatement
        implements LabelAble
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForStatement(
            Token           keyword,
            List<Statement> init,
            List<AstNode>   conds,
            List<Statement> update,
            StatementBlock  block)
        {
        super(keyword, conds);
        this.init    = init   == null ? Collections.emptyList() : init;
        this.update  = update == null ? Collections.emptyList() : update;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

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
     * @return true iff there is a continue label for this statement, which indicates that it has
     *         already been requested at least one time
     */
    public boolean hasContinueLabel()
        {
        return m_labelContinue != null;
        }

    /**
     * @return the continue label for this statement
     */
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label("continue_for_" + getLabelId());
            }
        return label;
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return     findInitializer(nodeChild) >= 0
                || findCondition  (nodeChild) >= 0
                || findUpdate     (nodeChild) >= 0;
        }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        AstNode nodeChild = findChild(nodeOrigin);
        assert nodeChild != null;
        assert allowsShortCircuit(nodeChild);

        if (findCondition(nodeChild) >= 0)
            {
            // short-circuiting a condition is the same as "breaking out of" the loop
            return ensureBreakLabel(nodeOrigin, ctxOrigin);
            }

        int     index = findInitializer(nodeChild);
        boolean fInit = index >= 0;
        int     count;
        String  desc;
        if (fInit)
            {
            count  = init.size();
            desc   = "init";
            }
        else
            {
            index  = findUpdate(nodeChild);
            count  = update.size();
            desc   = "update";
            assert index >= 0;
            }

        if (ctxOrigin.isReachable())
            {
            // short-circuiting an "initializer" or "update" proceeds to the next one
            Context ctxDest = ensureValidationContext();
            if (m_listShorts == null)
                {
                m_listShorts = new ArrayList<>();
                }
            m_listShorts.add(new SimpleEntry<>(nodeOrigin, ctxOrigin.prepareJump(ctxDest)));
            }

        // create, store, and return a label for this specific init/update node
        Label   label = new Label("ground_" + desc + "_" + index + "_of_" + count + "_for_" + getLabelId());
        Label[] labels = fInit ? m_alabelInitGround : m_alabelUpdateGround;
        if (labels == null)
            {
            labels = new Label[count];
            if (fInit)
                {
                m_alabelInitGround = labels;
                }
            else
                {
                m_alabelUpdateGround = labels;
                }
            }
        labels[index] = label;

        return label;
        }

    /**
     * Search the initializer list for the specified node, and return its index.
     *
     * @param node  the node to search for
     *
     * @return the index of the node in the initializer list, or -1 if the node is not in the list
     */
    protected int findInitializer(AstNode node)
        {
        if (node instanceof Statement)
            {
            for (int i = 0, c = init.size(); i < c; ++i)
                {
                if (node == init.get(i))
                    {
                    return i;
                    }
                }
            }
        return -1;
        }

    /**
     * Search the "update" list for the specified node, and return its index.
     *
     * @param node  the node to search for
     *
     * @return the index of the node in the update list, or -1 if the node is not in the list
     */
    protected int findUpdate(AstNode node)
        {
        if (node instanceof Statement)
            {
            for (int i = 0, c = update.size(); i < c; ++i)
                {
                if (node == update.get(i))
                    {
                    return i;
                    }
                }
            }
        return -1;
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
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

        // the for() statement will represent its own scope
        ctx = ctx.enter();

        // save off the current context and errors, in case we have to lazily create some loop vars
        m_ctxLabelVars  = ctx;
        m_errsLabelVars = errs;

        List<Statement> listInit = init;
        int             cInit    = listInit.size();
        for (int i = 0; i < cInit; ++i)
            {
            Statement stmtOld = listInit.get(i);
            Statement stmtNew = stmtOld.validate(ctx, errs);
            if (stmtNew == null)
                {
                fValid = false;
                }
            else if (stmtNew != stmtOld)
                {
                listInit.set(i, stmtNew);
                }

            if (m_listShorts != null)
                {
                for (Entry<AstNode, Map<String, Assignment>> entry : m_listShorts)
                    {
                    if (!entry.getKey().isDiscarded())
                        {
                        ctx.merge(entry.getValue());
                        }
                    }
                m_listShorts = null;
                }
            }

        // the test expression plays a role of an "if", since the block cannot be entered if this
        // expression evaluates to "false"
        ctx = ctx.enterIf();

        boolean fAlwaysTrue = true;
        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            AstNode cond = getCondition(i);

            // the condition is either a boolean expression or an assignment statement whose R-value
            // is a multi-value with the first value being a boolean
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtOld = (AssignmentStatement) cond;
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
                else if (exprNew != exprOld)
                    {
                    cond = exprNew;
                    conds.set(i, cond);
                    }
                }

            if (cond instanceof Expression && ((Expression) cond).isConstant())
                {
                if (((Expression) cond).isConstantFalse())
                    {
                    fAlwaysTrue = false;
                    }
                else
                    {
                    assert ((Expression) cond).isConstantTrue();
                    }
                }
            else
                {
                fAlwaysTrue  = false;
                }
            }

        // the statement block is equivalent to "if ... then"

        if (fAlwaysTrue)
            {
            if (init.isEmpty() && conds.isEmpty() &&
                    block.getStatements().isEmpty() && update.isEmpty())
                {
                log(errs, Severity.ERROR, Compiler.INFINITE_LOOP);
                return null;
                }
            ctx = ctx.enterInfiniteLoop();
            }
        else
            {
            ctx = ctx.enterFork(true);
            }

        StatementBlock blockOld = block;
        StatementBlock blockNew = (StatementBlock) blockOld.validate(ctx, errs);
        if (blockNew != blockOld)
            {
            if (blockNew == null)
                {
                fValid = false;
                }
            else
                {
                block = blockNew;
                }
            }

        if (m_listContinues != null)
            {
            // the last "continue" is translated as a "break"
            for (Entry<AstNode, Map<String, Assignment>> entry : m_listContinues)
                {
                if (!entry.getKey().isDiscarded())
                    {
                    ctx.merge(entry.getValue());
                    }
                }
            m_listContinues = null;
            }

        List<Statement> listUpdate = update;
        int             cUpdate    = listUpdate.size();
        for (int i = 0; i < cUpdate; ++i)
            {
            Statement stmtOld = listUpdate.get(i);
            Statement stmtNew = stmtOld.validate(ctx, errs);
            if (stmtNew == null)
                {
                fValid = false;
                }
            else if (stmtNew != stmtOld)
                {
                listUpdate.set(i, stmtNew);
                }

            if (m_listShorts != null)
                {
                for (Entry<AstNode, Map<String, Assignment>> entry : m_listShorts)
                    {
                    if (!entry.getKey().isDiscarded())
                        {
                        ctx.merge(entry.getValue());
                        }
                    }
                m_listShorts = null;
                }
            }

        // leaving the ForkContext
        ctx = ctx.exit();

        // leaving the IfContext
        ctx = ctx.exit();

        // leaving the scope of the for() statement
        ctx = ctx.exit();

        if (fAlwaysTrue)
            {
            // doesn't complete normally; the breaks will be processed by Statement.validate()
            ctx.setReachable(false);
            }

        // lazily created loop vars are only created inside the validation of this statement
        m_ctxLabelVars  = null;
        m_errsLabelVars = null;

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        code.add(new Enter());

        Register regFirst = m_regFirst;
        if (regFirst != null)
            {
            StringConstant name = pool().ensureStringConstant(((LabeledStatement) getParent()).getName() + ".first");
            code.add(new Var_IN(m_regFirst, name, pool().valTrue()));
            }

        Register regCount = m_regCount;
        if (regCount != null)
            {
            StringConstant name = pool().ensureStringConstant(((LabeledStatement) getParent()).getName() + ".count");
            code.add(new Var_IN(m_regCount, name, pool().val0()));
            }

        List<Statement> listInit   = init;
        int             cInit      = listInit.size();
        Label[]         aInitLabel = m_alabelInitGround;
        for (int i = 0; i < cInit; ++i)
            {
            fCompletes = listInit.get(i).completes(ctx, fCompletes, code, errs);

            Label labelGround = aInitLabel == null ? null : aInitLabel[i];
            if (labelGround != null)
                {
                code.add(labelGround);
                }
            }

        Label labelRepeat = new Label("loop_for_" + getLabelId());
        code.add(labelRepeat);

        // any condition of false results in false (as long as all conditions up to that point are
        // constant); all condition of true results in true (as long as all conditions are constant)
        boolean fAlwaysTrue  = true;
        boolean fAlwaysFalse = !conds.isEmpty(); // no conditions means "true"
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

        boolean fBlockReachable = fCompletes;
        if (fAlwaysFalse)
            {
            code.add(new Jump(getEndLabel()));
            fBlockReachable = false;
            }
        else if (!fAlwaysTrue)
            {
            for (int i = 0, c = getConditionCount(); i < c; ++i)
                {
                AstNode cond = getCondition(i);
                if (cond instanceof AssignmentStatement)
                    {
                    AssignmentStatement stmtCond = (AssignmentStatement) cond;
                    fBlockReachable &= stmtCond.completes(ctx, fCompletes, code, errs);

                    code.add(stmtCond.isNegated()
                            ? new JumpTrue (stmtCond.getConditionRegister(), getEndLabel())
                            : new JumpFalse(stmtCond.getConditionRegister(), getEndLabel()));
                    }
                else
                    {
                    Expression exprCond = (Expression) cond;
                    exprCond.generateConditionalJump(ctx, code, getEndLabel(), false, errs);
                    fBlockReachable &= exprCond.isCompletable();
                    }
                }
            }

        Op opLast = null;
        if (fAlwaysTrue)
            {
            opLast = code.getLastOp();
            block.suppressScope();
            }

        fCompletes &= block.completes(ctx, fBlockReachable, code, errs) || !fAlwaysTrue;

        if (hasContinueLabel())
            {
            code.add(getContinueLabel());
            }

        List<Statement> listUpdate   = update;
        int             cUpdate      = listUpdate.size();
        Label[]         aUpdateLabel = m_alabelUpdateGround;
        for (int i = 0; i < cUpdate; ++i)
            {
            fCompletes &= listUpdate.get(i).completes(ctx, fCompletes, code, errs) || !fAlwaysTrue;

            Label labelGround = aUpdateLabel == null ? null : aUpdateLabel[i];
            if (labelGround != null)
                {
                code.add(labelGround);
                }
            }

        if (regFirst != null)
            {
            code.add(new Move(pool().valFalse(), regFirst));
            }

        if (regCount != null)
            {
            code.add(new IP_Inc(regCount));
            }

        if (fAlwaysTrue && code.getLastOp() == opLast)
            {
            // the block didn't add any ops; this is just an infinite loop
            log(errs, Severity.ERROR, Compiler.INFINITE_LOOP);
            }
        else
            {
            code.add(new Jump(labelRepeat));
            code.add(new Exit());
            }

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("for (");

        if (init != null)
            {
            boolean first = true;
            for (Statement stmt : init)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append("; ");

        if (conds != null && !conds.isEmpty())
            {
            sb.append(conds.get(0));
            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
                }
            }

        sb.append("; ");

        if (update != null)
            {
            boolean first = true;
            for (Statement stmt : update)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Statement> init;
    protected List<Statement> update;
    protected StatementBlock  block;

    private transient Label m_labelContinue;

    private transient Context       m_ctxLabelVars;
    private transient ErrorListener m_errsLabelVars;
    private transient Register      m_regFirst;
    private transient Register      m_regCount;

    /**
     * Generally null, unless there is a "continue" that jumps to this statement.
     */
    private transient List<Map.Entry<AstNode,Map<String, Assignment>>> m_listContinues;

    /**
     * The short-circuits from inside of the most recent "init" or "update".
     */
    private transient List<Map.Entry<AstNode, Map<String, Assignment>>> m_listShorts;

    /**
     * The short-circuit grounding label for each "init". (Array or elements may be null.)
     */
    private transient Label[] m_alabelInitGround;

    /**
     * The short-circuit grounding label for each "update". (Array or elements may be null.)
     */
    private transient Label[] m_alabelUpdateGround;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForStatement.class, "init", "conds", "update", "block");
    }
