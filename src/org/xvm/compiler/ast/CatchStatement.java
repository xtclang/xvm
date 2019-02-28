package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.CatchEnd;
import org.xvm.asm.op.CatchStart;

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
        ctx = ctx.enter();
        ctx.markNonCompleting();

        // validate the catch clause
        VariableDeclarationStatement targetNew = (VariableDeclarationStatement) target.validate(ctx, errs);
        if (targetNew != null)
            {
            target = targetNew;

            if (!targetNew.getType().isA(pool().typeException()))
                {
                targetNew.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool().typeException().getValueString(), targetNew.getType().getValueString());
                }

            // validate the block
            StatementBlock blockNew = (StatementBlock) block.validate(ctx, errs);
            if (blockNew != null)
                {
                block = blockNew;
                }

            // contribute the variable assignment information from the catch back to the try statement,
            // since the normal completion of a try combined with the normal completion of all of its
            // catch clauses combines to provide the assignment impact of the try/catch
            // TODO
            }

        ctx.exit();

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        code.add(new CatchStart());
        boolean fCompletes = block.completes(ctx, )
        code.add(new CatchEnd());

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

    private static final Field[] CHILD_FIELDS = fieldsForNames(CatchStatement.class, "target", "block");
    }
