package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.CatchEnd;
import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "catch" statement. (Not actually a statement. It only occurs within a try.)
 */
public class CatchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public CatchStatement(VariableDeclarationStatement target, StatementBlock block, long lStartPos)
        {
        this.target    = target;
        this.block     = block;
        this.lStartPos = lStartPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the exception type
     */
    public TypeConstant getCatchType()
        {
        return target.getType();
        }

    /**
     * @return get the register used for the catch variable
     */
    public Register getCatchRegister()
        {
        return target.getRegister();
        }

    /**
     * @return the declared name of the catch variable
     */
    public String getCatchVariableName()
        {
        return target.getName();
        }

    public CatchStart ensureCatchStart()
        {
        CatchStart op = m_opCatch;
        if (op == null)
            {
            m_opCatch = op = new CatchStart(getCatchRegister(),
                    pool().ensureStringConstant(getCatchVariableName()));
            }
        return op;
        }

    public void setCatchLabel(Label label)
        {
        m_labelEndCatch = label;
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        ctx = ctx.enter();

        // validate the catch clause
        VariableDeclarationStatement targetNew = (VariableDeclarationStatement) target.validate(ctx, errs);
        if (targetNew == null)
            {
            fValid = false;
            }
        else
            {
            target = targetNew;

            // exception variable has a value at this point
            ctx.ensureDefiniteAssignments().put(target.getName(), Assignment.AssignedOnce);

            // make sure that the exception variable is of an exception type
            TypeConstant typeE = target.getType();
            if (!typeE.isA(pool().typeException()))
                {
                fValid = false;
                target.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool().typeException().getValueString(), typeE.getValueString());
                }

            // validate the block
            block.suppressScope();
            StatementBlock blockNew = (StatementBlock) block.validate(ctx, errs);
            if (blockNew != null)
                {
                block = blockNew;
                }
            }

        ctx.exit();

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        assert m_opCatch != null && m_labelEndCatch != null;

        code.add(m_opCatch);
        block.suppressScope();
        boolean fCompletes = block.completes(ctx, fReachable, code, errs);
        code.add(new CatchEnd(m_labelEndCatch));
        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "catch (" + target + ")\n" + indentLines(block.toString(), "    ");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected VariableDeclarationStatement target;
    protected StatementBlock               block;
    protected long                         lStartPos;

    private transient CatchStart m_opCatch;
    private transient Label      m_labelEndCatch;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CatchStatement.class, "target", "block");
    }