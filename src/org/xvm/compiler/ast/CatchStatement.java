package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

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
        // TODO

        // validate the block
        // TODO

        ctx.exit();

        // contribute the variable assignment information from the catch back to the try statement,
        // since the normal completion of a try combined with the normal completion of all of its
        // catch clauses combines to provide the assignment impact of the try/catch
        // TODO

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
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
